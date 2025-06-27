package com.chiou.javabridge.Models.Communication;

import com.google.gson.JsonElement;

public class MessageBase {
    public String Id;
    public String Platform;
    public String Handler;
    public String Event;
    public JsonElement Payload;

    public static MessageBase Create(String id, String platform, String handler, String event, JsonElement payload) {
        MessageBase base = new MessageBase();
        base.Id = id;
        base.Platform = platform;
        base.Handler = handler;
        base.Event = event;
        base.Payload = payload;

        return base;
    }

    public String GetPayload() {
        String stringPayload = Payload.toString();

        if (stringPayload.startsWith("\"") && stringPayload.endsWith("\"") && stringPayload.length() >= 2) {
            return stringPayload.substring(1, stringPayload.length() - 1);
        }
        return stringPayload;
    }
}
