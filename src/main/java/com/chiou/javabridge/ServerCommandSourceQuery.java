package com.chiou.javabridge;

import com.chiou.javabridge.Models.CommandSourceQuery;
import com.chiou.javabridge.Models.Communication.MessageBase;
import com.chiou.javabridge.Models.EventHandler;
import com.chiou.javabridge.Models.ICommandSourceQuery;
import com.chiou.javabridge.Models.ServerPlayerMap;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;

public class ServerCommandSourceQuery extends EventHandler implements ICommandSourceQuery {

    @Override
    public String GetPlatform() { return "SERVER"; }

    @Override
    public String GetHandler() { return "COMMAND"; }

    private final BridgeRequirementChecker _requirementChecker;
    private final Communicator _communicator;

    public ServerCommandSourceQuery(BridgeRequirementChecker requirementChecker, Communicator communicator) {
        this._requirementChecker = requirementChecker;
        this._communicator = communicator;
    }

    @Override
    public void HandleQuery(CommandSourceQuery commandQuery) {
        String[] split = commandQuery.Payload.split(":", 3);
        String query = split.length > 1 ? split[1] : commandQuery.Payload;
        // TODO: Validate that this is the correct required type inside (e.g. int)
        String additionalQuery = split.length > 2 ? split[2] : "";

        Object source = commandQuery.PendingSuggestions.get(commandQuery.ContextId);

        String finalValue = ResolveQuery(split[0], query, additionalQuery, source);

        _communicator.SendToHost(commandQuery.ClientId, AssembleMessage(commandQuery.Guid, "COMMAND_SOURCE_RESPONSE", commandQuery.ContextId + ":" + finalValue));
    }

    @Override
    public void HandleQuery(String clientId, MessageBase message, Map<String, Object> pendingCommands) {
        String[] split = message.GetPayload().split(":", 2);
        String contextId = split.length > 0 ? split[0] : "";
        String newPayload = split.length > 1 ? split[1] : message.GetPayload();

        // contextId could be commandId or suggestionContextId
        HandleQuery(new CommandSourceQuery(message.Id, clientId, "", contextId, newPayload, pendingCommands));
    }

    public String ResolveQuery(String commandName, String query, String additionalQuery, Object source) {
        if(source == null) {
            if(_requirementChecker.CommandSources.containsKey(commandName))
                source = _requirementChecker.CommandSources.remove(commandName);
        }

        ServerCommandSource clientSource = (ServerCommandSource)source;

        if (source != null) {
            String finalValue = "";

            switch (query) {
                case "IS_PLAYER" -> finalValue = String.valueOf(clientSource.isExecutedByPlayer());
                case "NAME" -> finalValue = String.valueOf(clientSource.getName());
                case "HASPERMISSIONLEVEL" -> finalValue = String.valueOf(clientSource.hasPermissionLevel(Integer.parseInt(additionalQuery)));
                case "DISPLAYNAME" -> finalValue = String.valueOf(clientSource.getDisplayName());
                case "ISSILENT" -> finalValue = String.valueOf(clientSource.isSilent());
                case "PLAYER_NAMES" -> finalValue = JavaBridge.Gson.toJson(clientSource.getPlayerNames());
            }

            if (query.startsWith("SEND_")) {
                HandleSend(clientSource, query, additionalQuery);
                finalValue = "OK";
            }
            else if (query.startsWith("PLAYER_")) {
                return HandlePlayer(clientSource, query, additionalQuery);
            }



//            clientSource.getEntity();
//            clientSource.getEntityAnchor();
//            clientSource.getPosition();
//            clientSource.getRegistryManager();
//            clientSource.getRotation();
//            clientSource.getServer();
//            clientSource.getWorld();
//            clientSource.withPosition();
//            clientSource.withEntity();
//            clientSource.withLevel();
//            clientSource.withLookingAt();
//            clientSource.withMaxLevel();
//            clientSource.withRotation();
//            clientSource.withSilent();

            return finalValue;
        } else {
            JavaBridge.LOGGER.warn("No pending command context for command: " + commandName);
            return "Error";
        }
    }

    private String HandlePlayer(ServerCommandSource clientSource, String query, String additionalQuery) {
        ServerPlayerEntity player = clientSource.getPlayer();

        if(player == null)
            return "Error";

        return ServerPlayerMap.GetValue(query, player);
        // Not there
//        player.capeX;
//        player.capeY;
//        player.capeZ;
    }

    private void HandleSend(ServerCommandSource source, String query, String additionalQuery) {
        switch (query) {
            case "SEND_CHAT" -> {
                // TODO: Implement
            }
            case "SEND_ERROR" -> source.sendError(Text.literal(additionalQuery));
            case "SEND_MESSAGE" -> source.sendMessage(Text.literal(additionalQuery));
            case "SEND_FEEDBACK" -> source.sendFeedback(() -> Text.literal(additionalQuery), false);
        }
    }
}