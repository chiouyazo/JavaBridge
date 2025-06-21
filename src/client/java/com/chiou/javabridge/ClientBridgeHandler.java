package com.chiou.javabridge;

import org.slf4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientBridgeHandler implements IClientMessageHandler {
    private Communicator _communicator;

    private final Logger _logger = JavaBridge.LOGGER;

    private final Map<String, String> _commandGuidMap = new ConcurrentHashMap<>();

    private final BridgeRequirementChecker _requirementChecker = new BridgeRequirementChecker(JavaBridge.Communicator);
//
//    private final DynamicCommandRegistrar registrar = new DynamicCommandRegistrar(false, (guid, commandName, payload) -> {
//        try {
//            _communicator.SendToHost(guid + ":CLIENT:COMMAND_EXECUTED:" + commandName + ":" + payload);
//        } catch (IOException e) {
//            _logger.error("Failed to send command executed event", e);
//        }
//        JavaBridge.LOGGER.info("Client command executed: " + commandName);
//    });

    public ClientBridgeHandler()
    {
        _communicator = JavaBridge.Communicator;
    }

    public void handleClientMessage(String guid, String platform, String event, String payload) throws IOException {
        System.out.println("Received client message: " + guid + " -> " + event + ": " + payload);

        // TODO: Add logic like opening a screen here
        switch (event) {
            case "REGISTER_COMMAND" -> handleRegisterCommand(guid, payload);
//            case "EXECUTE_COMMAND" -> handleExecuteCommand(guid, payload);
//            case "COMMAND_FEEDBACK" -> handleCommandFeedback(guid, payload);
//            case "COMMAND_FINALIZE" -> handleCommandFinalize(guid, payload);
//            case "COMMAND_REQUIREMENT_RESPONSE" -> {
//                pendingResponses.put(guid, payload);
//                synchronized (responseLock) {
//                    responseLock.notifyAll();
//                }
//            }
//
//            case "QUERY_COMMAND_SOURCE" -> handleCommandSourceQuery(guid, payload);
//            case "HELLO" -> handleNewMod(guid, payload);
            default -> JavaBridge.LOGGER.info("Unknown event: " + event);
        }
    }

    private void handleRegisterCommand(String guid, String commandDef) throws IOException {
//        String commandName = commandDef.split("\\|")[0];
//        _commandGuidMap.put(commandName, guid);
//
//        registrar.registerCommand(commandDef, _requirementChecker);

        _communicator.SendToHost(guid + ":CLIENT:COMMAND:COMMAND_REGISTERED:" + commandDef);
    }
}
