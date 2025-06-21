package com.chiou.javabridge;

import com.chiou.javabridge.Models.CommandHandler;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientCommandHandler extends CommandHandler {
    @Override
    public String GetPlatform() { return "CLIENT"; }
    @Override
    public String GetHandler() { return "COMMAND"; }

    private final Logger _logger = JavaBridge.LOGGER;

    private final Map<String, String> _commandGuidMap = new ConcurrentHashMap<>();
    private final ClientRequirementChecker _requirementChecker;
    private Communicator _communicator;

    private final Map<String, Object> PendingCommands = new ConcurrentHashMap<>();

    private final DynamicCommandRegistrar registrar = new DynamicCommandRegistrar(new ClientCommandRegistrarProxy(this, (guid, commandName, payload) -> {
        try {
            _communicator.SendToHost(AssembleMessage(guid, "COMMAND_EXECUTED", commandName + ":" + payload));
        } catch (IOException e) {
            _logger.error("Failed to send command executed event", e);
        }
    }));

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

    public void HandleRequest(String guid, String platform, String event, String payload) throws IOException {
        switch (event) {
            case "REGISTER_COMMAND" -> handleRegisterCommand(guid, payload);
            case "EXECUTE_COMMAND" -> handleExecuteCommand(guid, payload);
            case "COMMAND_FEEDBACK" -> handleCommandFeedback(guid, payload);
            case "COMMAND_FINALIZE" -> handleCommandFinalize(guid, payload);
            case "COMMAND_REQUIREMENT_RESPONSE" -> {
                _communicator.PendingResponses.put(guid, payload);
                synchronized (_communicator.ResponseLock) {
                    _communicator.ResponseLock.notifyAll();
                }
            }
            case "QUERY_COMMAND_SOURCE" -> handleCommandSourceQuery(guid, payload);

            default -> _logger.info("Unknown event: " + event);
        }
    }

    private void handleRegisterCommand(String guid, String commandDef) throws IOException {
        String commandName = commandDef.split("\\|")[0];
        _commandGuidMap.put(commandName, guid);

        registrar.registerCommand(commandDef, _requirementChecker);

        _communicator.SendToHost(AssembleMessage(guid, "COMMAND_REGISTERED", commandDef));
    }

    protected void handleExecuteCommand(String guid, String command) {
        if (JavaBridge.Server == null) {
            _communicator.SendSafe(AssembleMessage(guid, "COMMAND_RESULT", "Server not available"));
            return;
        }

        JavaBridge.Server.execute(() -> {
            try {
                ServerCommandSource source = JavaBridge.Server.getCommandSource();
                JavaBridge.Server.getCommandManager().executeWithPrefix(source, command);
                _communicator.SendSafe(AssembleMessage(guid, "COMMAND_RESULT", "Success"));
            } catch (Exception e) {
                _communicator.SendSafe(AssembleMessage(guid, "COMMAND_RESULT", "Error:" + e.getMessage()));
            }
        });
    }

    private void sendCommandExecuted(String commandName, String payload) throws IOException {
        String guid = _commandGuidMap.get(commandName);
        if (guid != null) {
            _communicator.SendToHost(AssembleMessage(guid, "COMMAND_EXECUTED", commandName + ":" + payload));
        } else {
            _logger.warn("No guid found for command " + commandName);
        }
    }

    private void handleCommandFeedback(String guid, String payload) throws IOException {
        String[] split = payload.split(":", 2);
        String message = split[1];
        String commandId = split[0];

        FabricClientCommandSource source = (FabricClientCommandSource) PendingCommands.get(commandId);
        if (source != null) {
            source.sendFeedback(Text.literal(message));

            _communicator.SendToHost(AssembleMessage(guid, "COMMAND_FEEDBACK", commandId + ":OK"));
        } else {
            _logger.warn("No pending command context for guid: " + commandId);
            _communicator.SendToHost(AssembleMessage(guid, "COMMAND_FEEDBACK", commandId + ":Error"));
        }
    }

    private void handleCommandFinalize(String guid, String commandId) throws IOException {
        PendingCommands.remove(commandId);
        _communicator.SendToHost(AssembleMessage(guid, "COMMAND_FINALIZE", commandId + ":OK"));
    }

    private void handleCommandSourceQuery(String guid, String payload) throws IOException {
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

            _communicator.SendToHost(AssembleMessage(guid, "COMMAND_SOURCE_RESPONSE", commandId + ":" + finalValue));
        } else {
            _logger.warn("No pending command context for guid: " + commandId);
            _communicator.SendToHost(AssembleMessage(guid, "COMMAND_FEEDBACK", commandId + ":Error"));
        }
    }
}
