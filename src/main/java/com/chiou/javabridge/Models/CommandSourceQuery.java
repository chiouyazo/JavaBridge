package com.chiou.javabridge.Models;

import java.util.Map;

public class CommandSourceQuery {
    public String Guid;
    public String ClientId;
    public String ProviderId;
    public String ContextId;
    public String Payload;
    public Map<String, Object> PendingSuggestions;

    public CommandSourceQuery(String guid, String clientId, String providerId, String contextId, String payload, Map<String, Object> pendingSuggestions) {
        Guid = guid;
        ClientId = clientId;
        ProviderId = providerId;
        ContextId = contextId;
        Payload = payload;
        PendingSuggestions = pendingSuggestions;
    }
}
