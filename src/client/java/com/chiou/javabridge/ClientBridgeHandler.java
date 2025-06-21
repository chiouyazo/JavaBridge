package com.chiou.javabridge;

import com.chiou.javabridge.Models.IClientMessageHandler;
import org.slf4j.Logger;

import java.io.IOException;

public class ClientBridgeHandler implements IClientMessageHandler {
    private Communicator _communicator;

    private final Logger _logger = JavaBridge.LOGGER;

    private final ClientCommandHandler _commandHandler;

    public ClientBridgeHandler()
    {
        _communicator = JavaBridge.Communicator;
        _commandHandler = new ClientCommandHandler(_communicator);
    }

    public void handleClientMessage(String guid, String platform, String handler, String event, String payload) throws IOException {
        // TODO: Add logic like opening a screen here
        switch (handler) {
            case "COMMAND" -> _commandHandler.HandleRequest(guid, platform, event, payload);

            case "SERVER" -> { }
            default -> _logger.info("Unknown handler: " + handler);
        }
    }
}
