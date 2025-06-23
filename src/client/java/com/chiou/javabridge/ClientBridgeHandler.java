package com.chiou.javabridge;

import com.chiou.javabridge.Models.IClientMessageHandler;
import org.slf4j.Logger;

import java.io.IOException;

public class ClientBridgeHandler implements IClientMessageHandler {
    private Communicator _communicator;

    private final Logger _logger = JavaBridge.LOGGER;

    private final ClientCommandHandler _commandHandler;
    private final ScreenHandler _screenHandler;

    public ClientBridgeHandler()
    {
        _communicator = JavaBridge.INSTANCE.Communicator;
        _commandHandler = new ClientCommandHandler(_communicator);
        _screenHandler = new ScreenHandler(_communicator);
    }

    public void handleClientMessage(String clientId, String guid, String platform, String handler, String event, String payload) throws IOException {
        // TODO: Add logic like opening a screen here
        switch (handler) {
            case "COMMAND" -> _commandHandler.HandleRequest(clientId, guid, platform, event, payload);
            case "SCREEN" -> _screenHandler.HandleRequest(clientId, guid, platform, event, payload);

            case "SERVER" -> { }
            default -> _logger.info("Unknown handler: " + handler);
        }
    }
}
