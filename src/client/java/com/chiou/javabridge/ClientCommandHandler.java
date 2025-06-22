package com.chiou.javabridge;

import com.chiou.javabridge.Models.CommandHandler;
import com.chiou.javabridge.Models.CommandNode;
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

    private final DynamicCommandRegistrar registrar = new DynamicCommandRegistrar(new ClientCommandRegistrarProxy(this, (guid, commandName, payload) ->
            _communicator.SendToHost(_commandToClientGuid.get(commandName), AssembleMessage(guid, "COMMAND_EXECUTED", commandName + "|" + payload))));

    public ClientCommandHandler(Communicator communicator) {
        _communicator = communicator;
        _requirementChecker = new ClientRequirementChecker(_communicator);
    }

    @Override
    public Map<String, Object> GetPendingCommands() {
        return PendingCommands;
    }

    @Override
    public void PutPendingCommand(String guid, Object source) {
        PendingCommands.put(guid, source);
    }

    public void HandleRequest(String clientId, String guid, String platform, String event, String payload) throws IOException {
        switch (event) {
            case "REGISTER_COMMAND" -> handleRegisterCommand(clientId, guid, payload);
            case "EXECUTE_COMMAND" -> handleExecuteCommand(clientId, guid, payload);
            case "COMMAND_FEEDBACK" -> handleCommandFeedback(clientId, guid, payload);
            case "COMMAND_FINALIZE" -> handleCommandFinalize(clientId, guid, payload);
            case "COMMAND_REQUIREMENT_RESPONSE" -> {
                _communicator.PendingResponses.put(guid, payload);
                synchronized (_communicator.ResponseLock) {
                    _communicator.ResponseLock.notifyAll();
                }
            }
            case "QUERY_COMMAND_SOURCE" -> handleCommandSourceQuery(clientId, guid, payload);

            default -> _logger.info("Unknown event: " + event);
        }
    }

    private void handleRegisterCommand(String clientId, String guid, String commandDef) {
        String commandName = commandDef.split("\\|")[0];

        CommandNode commandNode = registrar.ParseCommandNode(commandDef, _requirementChecker);

        _commandGuidMap.put(commandName, guid);
        _commandToClientGuid.put(commandName, clientId);
        MapCommandsClient(commandNode.subCommands, clientId, commandName);

        registrar.registerCommand(clientId, commandDef, _requirementChecker);

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

    protected void handleExecuteCommand(String clientId, String guid, String command) {
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

        FabricClientCommandSource source = (FabricClientCommandSource) PendingCommands.get(commandId);
        if (source != null) {
            source.sendFeedback(Text.literal(message));

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

    private void handleCommandSourceQuery(String clientId, String guid, String payload) throws IOException {
        String[] split = payload.split(":", 4);
        String commandId = split.length > 0 ? split[0] : "";
        String commandName = split.length > 1 ? split[1] : "";
        String query = split.length > 2 ? split[2] : "";
        // TODO: Validate that this is the correct required type inside (e.g. int)
        String additionalQuery = split.length > 3 ? split[3] : "";

        FabricClientCommandSource source = (FabricClientCommandSource) PendingCommands.get(commandId);
        // When a world is loaded, all permissions are checked, thus we need this source temporarily
        if(source == null) {
            if(_requirementChecker.CommandSources.containsKey(commandName))
                source = _requirementChecker.CommandSources.remove(commandName);
        }
        if (source != null) {
            String finalValue = "";

            switch (query) {
                case "IS_PLAYER" -> finalValue = String.valueOf(true);
                case "NAME" -> finalValue = String.valueOf(source.getPlayer().getName());
                case "HASPERMISSIONLEVEL" -> finalValue = String.valueOf(source.hasPermissionLevel(Integer.parseInt(additionalQuery)));
            }

            _communicator.SendToHost(clientId, AssembleMessage(guid, "COMMAND_SOURCE_RESPONSE", commandId + ":" + finalValue));
        } else {
            _logger.warn("No pending command context for guid: " + commandId);
            _communicator.SendToHost(clientId, AssembleMessage(guid, "COMMAND_FEEDBACK", commandId + ":Error"));
        }
    }
}
