package com.chiou.javabridge;

import java.io.IOException;

public interface IClientMessageHandler {
    void handleClientMessage(String guid, String platform, String event, String payload) throws IOException;
}
