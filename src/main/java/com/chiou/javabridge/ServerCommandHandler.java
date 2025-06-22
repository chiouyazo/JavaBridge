package com.chiou.javabridge;

import com.chiou.javabridge.Models.CommandNode;
import com.chiou.javabridge.Models.SuggestionProviderBase;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerCommandHandler extends com.chiou.javabridge.Models.CommandHandler {

    @Override
    public String GetPlatform() { return "SERVER"; }

    @Override
    public String GetHandler() { return "COMMAND"; }


    private final Logger _logger = JavaBridge.LOGGER;

    private final Map<String, String> _commandGuidMap = new ConcurrentHashMap<>();
    private final Map<String, String> _commandToClientGuid = new ConcurrentHashMap<>();

    private final BridgeRequirementChecker _requirementChecker;
    private Communicator _communicator;

    private final Map<String, Object> PendingCommands = new ConcurrentHashMap<>();

    private final DynamicCommandRegistrar _registrar;
    private final ServerCommandSourceQuery _sourceQuery;

    public ServerCommandHandler(Communicator communicator) {
        _communicator = communicator;
        _requirementChecker = new BridgeRequirementChecker(_communicator);
        _sourceQuery = new ServerCommandSourceQuery(_requirementChecker, _communicator);

        _registrar = new DynamicCommandRegistrar(new ServerCommandRegistrarProxy(this, _communicator,
                (guid, commandName, payload) ->
                _communicator.SendToHost(_commandToClientGuid.get(commandName), guid + ":" + GetPlatform() + ":COMMAND:COMMAND_EXECUTED:" + commandName + "|" + payload),

                _sourceQuery::HandleQuery));
    }

    @Override
    public Map<String, Object> GetPendingCommands() {
        return PendingCommands;
    }

    @Override
    public void PutPendingCommand(String guid, Object source) {
        PendingCommands.put(guid, (ServerCommandSource) source);
    }

    public void HandleRequest(String clientId, String guid, String platform, String event, String payload) throws IOException {
        switch (event) {
            case "REGISTER_COMMAND" -> handleRegisterCommand(clientId, guid, payload);
            case "EXECUTE_COMMAND" -> handleExecuteCommand(clientId, guid, payload);
            case "COMMAND_FEEDBACK" -> handleCommandFeedback(clientId, guid, payload);
            case "COMMAND_FINALIZE" -> handleCommandFinalize(clientId, guid, payload);
            case "COMMAND_REQUIREMENT_RESPONSE", "SUGGESTION_RESPONSE" -> {
                _communicator.PendingResponses.put(guid, payload);
                synchronized (_communicator.ResponseLock) {
                    _communicator.ResponseLock.notifyAll();
                }
            }
            case "SUGGESTION_FINALIZE" -> handleSuggestionFinalize(clientId, guid, payload);

            case "QUERY_COMMAND_SOURCE" -> handleCommandSourceQuery(clientId, guid, payload);
            case "QUERY_SUGGESTION_SOURCE" -> handleSuggestionSourceQuery(clientId, guid, payload);

            default -> _logger.info("Unknown event: " + event);
        }
    }

    private void handleRegisterCommand(String clientId, String guid, String commandDef) {
        String commandName = commandDef.split("\\|")[0];

        CommandNode commandNode = _registrar.ParseCommandNode(commandDef, _requirementChecker);

        _commandGuidMap.put(commandName, guid);
        _commandToClientGuid.put(commandName, clientId);
        MapCommandsClient(commandNode.subCommands, clientId, commandName);

        _registrar.registerCommand(clientId, commandDef, _requirementChecker);

        _communicator.SendToHost(clientId, AssembleMessage(guid, "COMMAND_REGISTERED", commandDef));
    }

    private void MapCommandsClient(List<CommandNode> subCommands, String clientId, String parentPath) {
        for (CommandNode command : subCommands) {
            String localPath = parentPath + ":" + command.Name;
            _commandToClientGuid.put(localPath, clientId);

            if (!command.subCommands.isEmpty())
                MapCommandsClient(command.subCommands, clientId, localPath);
        }
    }

    private void handleExecuteCommand(String clientId, String guid, String command) {
        if (JavaBridge.Server == null) {
            _communicator.SendToHost(clientId, AssembleMessage(guid, "COMMAND_RESULT", "Server not available"));
            return;
        }

        JavaBridge.Server.execute(() -> {
            try {
                ServerCommandSource source = JavaBridge.Server.getCommandSource();
                JavaBridge.Server.getCommandManager().executeWithPrefix(source, command);
                _communicator.SendToHost(clientId, AssembleMessage(guid, "COMMAND_RESULT", "Success"));
            } catch (Exception e) {
                _communicator.SendToHost(clientId, AssembleMessage(guid, "COMMAND_RESULT", "Error:" + e.getMessage()));
            }
        });
    }

    private void handleCommandFeedback(String clientId, String guid, String payload) throws IOException {
        String[] split = payload.split(":", 2);
        String message = split[1];
        String commandId = split[0];

        ServerCommandSource source = (ServerCommandSource) PendingCommands.get(commandId);
        if (source != null) {
            source.sendFeedback(() -> Text.literal(message), false);

            _communicator.SendToHost(clientId, AssembleMessage(guid, "COMMAND_FEEDBACK", commandId + ":OK"));
        } else {
            _logger.warn("No pending command context for guid: " + commandId);
            _communicator.SendToHost(clientId, AssembleMessage(guid, "COMMAND_FEEDBACK", commandId + ":Error"));
        }
    }

    private void handleCommandFinalize(String clientId, String guid, String commandId) throws IOException {
        PendingCommands.remove(commandId);
        _communicator.SendToHost(clientId, AssembleMessage(guid, "COMMAND_FINALIZE", commandId + ":OK"));
    }

    private void handleSuggestionFinalize(String clientId, String guid, String payload) throws IOException {
        String[] parts = payload.split(":", 2);
        if (parts.length < 2) {
            _logger.warn("Invalid SUGGESTION_SOURCE payload: {}", payload);
            return;
        }

        String providerId = parts[0];

        SuggestionProviderBase provider = _registrar.GetProvider(providerId);

        if (provider == null) {
            _logger.warn("No suggestion provider found for ID: {}", providerId);
            return;
        }

        provider.FinalizeSuggestion(guid);
        _communicator.SendToHost(clientId, AssembleMessage(guid, "SUGGESTION_FINALIZE", providerId + ":OK"));
    }

    private void handleCommandSourceQuery(String clientId, String guid, String payload) {
        _sourceQuery.HandleQuery(clientId, guid, payload, PendingCommands);
    }

    private void handleSuggestionSourceQuery(String clientId, String guid, String payload) {
        String[] parts = payload.split(":", 3);
        if (parts.length < 3) {
            _logger.warn("Invalid SUGGESTION_SOURCE payload: {}", payload);
            return;
        }

        String providerId = parts[0];
        String contextId = parts[1];
        String queryPayload = parts[2];

        SuggestionProviderBase provider = _registrar.GetProvider(providerId);

        if (provider == null) {
            _logger.warn("No suggestion provider found for ID: {}", providerId);
            return;
        }

        provider.HandleQuery(guid, contextId, queryPayload);
    }
}
