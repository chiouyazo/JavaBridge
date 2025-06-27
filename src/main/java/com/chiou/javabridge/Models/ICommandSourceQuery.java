package com.chiou.javabridge.Models;

import com.chiou.javabridge.Models.Communication.MessageBase;

import java.util.Map;

public interface ICommandSourceQuery {
    public void HandleQuery(CommandSourceQuery commandQuery);
    public void HandleQuery(String clientId, MessageBase message, Map<String, Object> pendingCommands);
    public String ResolveQuery(String commandName, String query, String additionalQuery, Object source);
}
