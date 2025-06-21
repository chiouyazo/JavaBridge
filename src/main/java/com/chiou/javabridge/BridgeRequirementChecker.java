package com.chiou.javabridge;

import com.chiou.javabridge.Models.IRequirementChecker;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BridgeRequirementChecker implements IRequirementChecker {
    private final String PLATFORM = "SERVER";
    private final Communicator _comm;

    public final Map<String, ServerCommandSource> CommandSources = new ConcurrentHashMap<>();

    public BridgeRequirementChecker(Communicator comm) {
        _comm = comm;
    }

    public boolean check(Object source, String commandName) {
        try {
            CommandSources.put(commandName, (ServerCommandSource) source);
            String requestId = UUID.randomUUID().toString();

            _comm.SendToHost(requestId + ":" + PLATFORM + ":COMMAND:COMMAND_REQUIREMENT:" + commandName);

            // Wait for response (simple blocking queue or synchronized wait/notify)
            String response = _comm.waitForResponseAsync(requestId, 50000).get();

            return response.equals("True");
        } catch (Exception e) {
            JavaBridge.LOGGER.error("Failed to check requirement for command " + commandName, e);
            return false;
        }
    }
}