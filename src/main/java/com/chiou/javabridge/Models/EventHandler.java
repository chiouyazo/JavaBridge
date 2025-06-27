package com.chiou.javabridge.Models;

import com.chiou.javabridge.Models.Communication.MessageBase;
import com.google.gson.Gson;

public abstract class EventHandler {
    public abstract String GetPlatform();
    public abstract String GetHandler();

    protected MessageBase AssembleMessage(String guid, String event, String payload) {
        Gson gson = new Gson();
        return MessageBase.Create(guid, GetPlatform(), GetHandler(), event, gson.toJsonTree(payload));
    }
}