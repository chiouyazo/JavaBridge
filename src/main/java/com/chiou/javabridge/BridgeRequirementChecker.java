package com.chiou.javabridge;

import net.minecraft.server.command.ServerCommandSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BridgeRequirementChecker {
    private final JavaBridge bridge;

    public final Map<String, ServerCommandSource> CommandSources = new ConcurrentHashMap<>();

    public BridgeRequirementChecker(JavaBridge bridge) {
        this.bridge = bridge;
    }

    public boolean check(ServerCommandSource source, String commandName) {
        try {
            CommandSources.put(commandName, source);
            String requestId = UUID.randomUUID().toString();

            bridge.sendToCSharp(requestId + ":COMMAND_REQUIREMENT:" + commandName);

            // Wait for response (simple blocking queue or synchronized wait/notify)
            String response = bridge.waitForResponseAsync(requestId, 50000).get();

            return response.equals("True");
        } catch (Exception e) {
            JavaBridge.LOGGER.error("Failed to check requirement for command " + commandName, e);
            return false;
        }
    }
}
