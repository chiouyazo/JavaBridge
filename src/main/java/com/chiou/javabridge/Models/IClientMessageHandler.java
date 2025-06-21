package com.chiou.javabridge.Models;

import java.io.IOException;

public interface IClientMessageHandler {
    void handleClientMessage(String clientId, String guid, String platform, String handler, String event, String payload) throws IOException;
}
