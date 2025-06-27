package com.chiou.javabridge;

import com.chiou.javabridge.Models.IClientMessageHandler;
import com.chiou.javabridge.Models.Communication.MessageBase;
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

    public void handleClientMessage(String clientId, MessageBase message) throws IOException {
        // TODO: Add logic like opening a screen here
        switch (message.Handler) {
            case "COMMAND" -> _commandHandler.HandleRequest(clientId, message);
            case "SCREEN" -> _screenHandler.HandleRequest(clientId, message);

            case "SERVER" -> { }
            default -> _logger.info("Unknown handler: " + message.Handler);
        }
    }
}
