package com.chiou.javabridge;

import com.chiou.javabridge.Models.CommandHandler;
import com.chiou.javabridge.Models.CommandNode;
import com.chiou.javabridge.Models.Communication.MessageBase;
import com.chiou.javabridge.Models.SuggestionProviderBase;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientCommandHandler extends CommandHandler {
    @Override
    public String GetPlatform() { return "CLIENT"; }
    @Override
    public String GetHandler() { return "COMMAND"; }

    private final Logger _logger = JavaBridge.LOGGER;

    private final Map<String, String> _commandGuidMap = new ConcurrentHashMap<>();
    private final Map<String, String> _commandToClientGuid = new ConcurrentHashMap<>();
    private final ClientRequirementChecker _requirementChecker;
    private Communicator _communicator;

    private final Map<String, Object> PendingCommands = new ConcurrentHashMap<>();

    private final DynamicCommandRegistrar _registrar;
    private final ClientCommandSourceQuery _sourceQuery;

    public ClientCommandHandler(Communicator communicator) {
        _communicator = communicator;
        _requirementChecker = new ClientRequirementChecker(_communicator);
        _sourceQuery = new ClientCommandSourceQuery(_requirementChecker, _communicator);

        _registrar = new DynamicCommandRegistrar(new ClientCommandRegistrarProxy(this, _communicator,
                (guid, commandName, payload) ->
                _communicator.SendToHost(_commandToClientGuid.get(commandName), AssembleMessage(guid, "COMMAND_EXECUTED", commandName + "|" + payload)),

                _sourceQuery::HandleQuery));
    }

    @Override
    public Map<String, Object> GetPendingCommands() {
        return PendingCommands;
    }

    @Override
    public void PutPendingCommand(String guid, Object source) {
        PendingCommands.put(guid, source);
    }

    public void HandleRequest(String clientId, MessageBase message) throws IOException {
        switch (message.Event) {
            case "REGISTER_COMMAND" -> handleRegisterCommand(clientId, message);
            case "EXECUTE_COMMAND" -> handleExecuteCommand(clientId, message);
            case "COMMAND_FEEDBACK" -> handleCommandFeedback(clientId, message);
            case "COMMAND_FINALIZE" -> handleCommandFinalize(clientId, message);
            case "COMMAND_REQUIREMENT_RESPONSE", "SUGGESTION_RESPONSE" -> {
                _communicator.PendingResponses.put(message.Id, message.GetPayload());
                synchronized (_communicator.ResponseLock) {
                    _communicator.ResponseLock.notifyAll();
                }
            }
            case "SUGGESTION_FINALIZE" -> handleSuggestionFinalize(clientId, message);

            case "QUERY_COMMAND_SOURCE" -> handleCommandSourceQuery(clientId, message);
            case "QUERY_SUGGESTION_SOURCE" -> handleSuggestionSourceQuery(clientId, message);

            default -> _logger.info("Unknown event: " + message.Event);
        }
    }

    private void handleRegisterCommand(String clientId, MessageBase message) {
        String commandName = message.GetPayload().split("\\|")[0];

        CommandNode commandNode = _registrar.ParseCommandNode(message.GetPayload(), _requirementChecker);

        _commandGuidMap.put(commandName, message.Id);
        _commandToClientGuid.put(commandName, clientId);
        MapCommandsClient(commandNode.subCommands, clientId, commandName);

        _registrar.registerCommand(clientId, message.GetPayload(), _requirementChecker);

        _communicator.SendToHost(clientId, AssembleMessage(message.Id, "COMMAND_REGISTERED", message.GetPayload()));
    }

    private void MapCommandsClient(List<CommandNode> subCommands, String clientId, String parentPath) {
        for (CommandNode command : subCommands) {
            String localPath = parentPath + ":" + command.Name;
            _commandToClientGuid.put(localPath, clientId);

            if (!command.subCommands.isEmpty())
                MapCommandsClient(command.subCommands, clientId, localPath);
        }
    }

    private void handleCommandFeedback(String clientId, MessageBase messageBase) throws IOException {
        String[] split = messageBase.GetPayload().split(":", 2);
        String message = split[1];
        String commandId = split[0];

        FabricClientCommandSource source = (FabricClientCommandSource) PendingCommands.get(commandId);
        if (source != null) {
            source.sendFeedback(Text.literal(message));

            _communicator.SendToHost(clientId, AssembleMessage(messageBase.Id, "COMMAND_FEEDBACK", commandId + ":OK"));
        } else {
            _logger.warn("No pending command context for guid: " + commandId);
            _communicator.SendToHost(clientId, AssembleMessage(messageBase.Id, "COMMAND_FEEDBACK", commandId + ":Error"));
        }
    }

    protected void handleExecuteCommand(String clientId, MessageBase message) {
        if (JavaBridge.Server == null) {
            _communicator.SendToHost(clientId, AssembleMessage(message.Id, "COMMAND_RESULT", "Server not available"));
            return;
        }

        JavaBridge.Server.execute(() -> {
            try {
                ServerCommandSource source = JavaBridge.Server.getCommandSource();
                JavaBridge.Server.getCommandManager().executeWithPrefix(source, message.GetPayload());
                _communicator.SendToHost(clientId, AssembleMessage(message.Id, "COMMAND_RESULT", "Success"));
            } catch (Exception e) {
                _communicator.SendToHost(clientId, AssembleMessage(message.Id, "COMMAND_RESULT", "Error:" + e.getMessage()));
            }
        });
    }

    private void handleCommandFinalize(String clientId, MessageBase message) throws IOException {
        PendingCommands.remove(message.GetPayload());
        _communicator.SendToHost(clientId, AssembleMessage(message.Id, "COMMAND_FINALIZE", message.GetPayload() + ":OK"));
    }

    private void handleSuggestionFinalize(String clientId, MessageBase message) throws IOException {
        String[] parts = message.GetPayload().split(":", 2);
        if (parts.length < 2) {
            _logger.warn("Invalid SUGGESTION_SOURCE payload: {}", message.GetPayload());
            return;
        }

        String providerId = parts[0];

        SuggestionProviderBase provider = _registrar.GetProvider(providerId);

        if (provider == null) {
            _logger.warn("No suggestion provider found for ID: {}", providerId);
            return;
        }

        provider.FinalizeSuggestion(message.Id);
        _communicator.SendToHost(clientId, AssembleMessage(message.Id, "SUGGESTION_FINALIZE", providerId + ":OK"));
    }

    private void handleCommandSourceQuery(String clientId, MessageBase message) {
        _sourceQuery.HandleQuery(clientId, message, PendingCommands);
    }

    private void handleSuggestionSourceQuery(String clientId, MessageBase message) {
        String[] parts = message.GetPayload().split(":", 3);
        if (parts.length < 3) {
            _logger.warn("Invalid SUGGESTION_SOURCE payload: {}", message.GetPayload());
            return;
        }

        String providerId = parts[0];
        String contextId = parts[1];
        String queryPayload = parts[1];

        SuggestionProviderBase provider = _registrar.GetProvider(providerId);

        if (provider == null) {
            _logger.warn("No suggestion provider found for ID: {}", providerId);
            return;
        }

        provider.HandleQuery(message.Id, contextId, queryPayload);
    }
}
