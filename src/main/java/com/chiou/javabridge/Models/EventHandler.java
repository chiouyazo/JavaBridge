package com.chiou.javabridge.Models;

public abstract class EventHandler {
    public abstract String GetPlatform();
    public abstract String GetHandler();

    protected String AssembleMessage(String guid, String event, String payload) {
        return guid + ":" + GetPlatform() + ":" + GetHandler() + ":" + event + ":" + payload;
    }
}