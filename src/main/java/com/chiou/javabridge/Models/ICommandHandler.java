package com.chiou.javabridge.Models;

import net.minecraft.server.command.ServerCommandSource;

import java.io.IOException;
import java.util.Map;

public interface ICommandHandler {
    Map<String, Object> GetPendingCommands();
    void PutPendingCommand(String guid, Object source);
    void HandleRequest(String guid, String platform, String event, String payload) throws IOException;
}
