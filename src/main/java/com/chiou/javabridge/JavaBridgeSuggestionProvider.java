package com.chiou.javabridge;

import com.chiou.javabridge.Models.EventHandler;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class JavaBridgeSuggestionProvider extends EventHandler implements SuggestionProvider<ServerCommandSource> {
    @Override
    public String GetPlatform() { return "SERVER"; }
    @Override
    public String GetHandler() { return "COMMAND"; }

    private final String providerId;
    private final String clientId;
    private final Communicator communicator;

    public JavaBridgeSuggestionProvider(String providerId, String clientId, Communicator communicator) {
        this.providerId = providerId;
        this.clientId = clientId;
        this.communicator = communicator;
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) throws CommandSyntaxException {
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