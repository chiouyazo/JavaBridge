package com.chiou.javabridge;

import com.chiou.javabridge.Models.EventHandler;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ClientSuggestionProvider extends EventHandler implements SuggestionProvider<FabricClientCommandSource> {
    @Override
    public String GetPlatform() { return "CLIENT"; }
    @Override
    public String GetHandler() { return "COMMAND"; }

    private final String providerId;
    private final String clientId;
    private final Communicator communicator;

    public ClientSuggestionProvider(String providerId, String clientId, Communicator communicator) {
        this.providerId = providerId;
        this.clientId = clientId;
        this.communicator = communicator;
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) throws CommandSyntaxException {
        String requestId = UUID.randomUUID().toString();

        String payload = builder.getRemaining();

        String message = AssembleMessage(requestId, "SUGGESTION_REQUEST", providerId + ":" + payload);
        communicator.SendToHost(clientId, message);

        // Await response asynchronously
        return communicator.waitForResponseAsync(requestId, 50000).thenApply(suggestions -> {
            for (String suggestion : suggestions.split(",")) {
                builder.suggest(suggestion);
            }
            return builder.build();
        });
    }
}