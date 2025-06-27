package com.chiou.javabridge;

import com.chiou.javabridge.Models.EventHandler;
import com.chiou.javabridge.Models.IRequirementChecker;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientRequirementChecker extends EventHandler implements IRequirementChecker {
    @Override
    public String GetPlatform() { return "CLIENT"; }

    @Override
    public String GetHandler() { return "COMMAND"; }

    private final Communicator _comm;

    public final Map<String, FabricClientCommandSource> CommandSources = new ConcurrentHashMap<>();

    public ClientRequirementChecker(Communicator comm) {
        _comm = comm;
    }

    public boolean check(String clientId, Object source, String commandName) {
        try {
            CommandSources.put(commandName, (FabricClientCommandSource)source);
            String requestId = UUID.randomUUID().toString();

            _comm.SendToHost(clientId,  AssembleMessage(requestId, "COMMAND_REQUIREMENT", commandName));

            // Wait for response (simple blocking queue or synchronized wait/notify)
            String response = _comm.waitForResponseAsync(requestId, 50000).get().toString();

            return response.equals("True");
        } catch (Exception e) {
            JavaBridge.LOGGER.error("Failed to check requirement for command " + commandName, e);
            return false;
        }
    }
}