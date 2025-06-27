package com.chiou.javabridge.Models;

import com.chiou.javabridge.Models.Communication.MessageBase;

import java.io.IOException;

public interface IClientMessageHandler {
    void handleClientMessage(String clientId, MessageBase message) throws IOException;
}
