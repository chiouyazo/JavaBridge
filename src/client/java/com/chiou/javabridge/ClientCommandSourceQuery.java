package com.chiou.javabridge;

import com.chiou.javabridge.Models.CommandSourceQuery;
import com.chiou.javabridge.Models.Communication.MessageBase;
import com.chiou.javabridge.Models.EventHandler;
import com.chiou.javabridge.Models.ICommandSourceQuery;
import com.chiou.javabridge.Models.ClientPlayerMap;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

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
        String[] split = commandQuery.Payload.toString().split(":", 3);
        String query = split.length > 1 ? split[1] : commandQuery.Payload;
        // TODO: Validate that this is the correct required type inside (e.g. int)
        String additionalQuery = split.length > 1 ? split[1] : "";

        Object source = commandQuery.PendingSuggestions.get(commandQuery.Payload);

        String finalValue = ResolveQuery(split[0], query, additionalQuery, source);

        _communicator.SendToHost(commandQuery.ClientId, AssembleMessage(commandQuery.Guid, "COMMAND_SOURCE_RESPONSE", commandQuery.ContextId + ":" + finalValue));
    }

    @Override
    public void HandleQuery(String clientId, MessageBase message, Map<String, Object> pendingCommands) {
        String[] split = message.GetPayload().split(":", 2);
        String contextId = split.length > 0 ? split[0] : "";

        // contextId could be commandId or suggestionContextId
        HandleQuery(new CommandSourceQuery(message.Id, clientId, "", contextId, message.GetPayload(), pendingCommands));
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

            if (query.startsWith("SEND_")) {
                HandleSend(clientSource, query, additionalQuery);
                finalValue = "OK";
            }
            else if (query.startsWith("PLAYER_")) {
                return HandlePlayer(clientSource, query, additionalQuery);
            }

            return finalValue;
        } else {
            JavaBridge.LOGGER.warn("No pending command context for command: " + commandName);
            return "Error";
        }
    }

    private String HandlePlayer(FabricClientCommandSource clientSource, String query, String additionalQuery) {
        ClientPlayerEntity player = clientSource.getPlayer();

        if(player == null)
            return "Error";

        return ClientPlayerMap.GetValue(query, player);
    }

    private void HandleSend(FabricClientCommandSource source, String query, String additionalQuery) {
        switch (query) {
            case "SEND_CHAT" -> {
                // TODO: Implement
            }
            case "SEND_ERROR" -> source.sendError(Text.literal(additionalQuery));
            // case "SEND_MESSAGE" -> ;
            case "SEND_FEEDBACK" -> source.sendFeedback(Text.literal(additionalQuery));
        }
    }
}
