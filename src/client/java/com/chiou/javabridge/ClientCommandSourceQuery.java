package com.chiou.javabridge;

import com.chiou.javabridge.Models.CommandSourceQuery;
import com.chiou.javabridge.Models.EventHandler;
import com.chiou.javabridge.Models.ICommandSourceQuery;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.Map;

public class ClientCommandSourceQuery extends EventHandler implements ICommandSourceQuery {

    @Override
    public String GetPlatform() { return "CLIENT"; }

    @Override
    public String GetHandler() { return "COMMAND"; }

    private final ClientRequirementChecker _requirementChecker;
    private final Communicator _communicator;

    public ClientCommandSourceQuery(ClientRequirementChecker requirementChecker, Communicator communicator) {
        this._requirementChecker = requirementChecker;
        this._communicator = communicator;
    }

    @Override
    public void HandleQuery(CommandSourceQuery commandQuery) {
        String[] split = commandQuery.Payload.split(":", 2);
        String query = split.length > 0 ? split[0] : "";
        // TODO: Validate that this is the correct required type inside (e.g. int)
        String additionalQuery = split.length > 1 ? split[1] : "";

        Object source = commandQuery.PendingSuggestions.get(commandQuery.ContextId);

        String finalValue = ResolveQuery(commandQuery.ContextId, query, additionalQuery, source);

        _communicator.SendToHost(commandQuery.ClientId, AssembleMessage(commandQuery.Guid, "COMMAND_SOURCE_RESPONSE", commandQuery.ContextId + ":" + finalValue));
    }

    @Override
    public void HandleQuery(String clientId, String guid, String payload, Map<String, Object> pendingCommands) {
        String[] split = payload.split(":", 2);
        String contextId = split.length > 0 ? split[0] : "";

        // contextId could be commandId or suggestionContextId
        HandleQuery(new CommandSourceQuery(guid, clientId, "", contextId, payload, pendingCommands));
    }

    public String ResolveQuery(String commandName, String query, String additionalQuery, Object source) {
        if(source == null) {
            if(_requirementChecker.CommandSources.containsKey(commandName))
                source = _requirementChecker.CommandSources.remove(commandName);
        }

        FabricClientCommandSource clientSource = (FabricClientCommandSource)source;

        if (source != null) {
            String finalValue = "";

            switch (query) {
                case "IS_PLAYER" -> finalValue = String.valueOf(true);
                case "NAME" -> finalValue = String.valueOf(clientSource.getPlayer().getName());
                case "HASPERMISSIONLEVEL" -> finalValue = String.valueOf(clientSource.hasPermissionLevel(Integer.parseInt(additionalQuery)));
            }

            return finalValue;
        } else {
            JavaBridge.LOGGER.warn("No pending command context for command: " + commandName);
            return "Error";
        }
    }
}
