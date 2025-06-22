package com.chiou.javabridge.Models;

import java.util.Map;

public interface ICommandSourceQuery {
    public void HandleQuery(CommandSourceQuery commandQuery);
    public void HandleQuery(String clientId, String guid, String payload, Map<String, Object> pendingCommands);
    public String ResolveQuery(String commandName, String query, String additionalQuery, Object source);
}
