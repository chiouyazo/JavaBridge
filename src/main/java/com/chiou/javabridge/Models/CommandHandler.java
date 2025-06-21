package com.chiou.javabridge.Models;

import java.io.IOException;
import java.util.Map;

public abstract class CommandHandler extends EventHandler {
    public abstract Map<String, Object> GetPendingCommands();
    public abstract void PutPendingCommand(String guid, Object source);
    public abstract void HandleRequest(String clientId, String guid, String platform, String event, String payload) throws IOException;
}
