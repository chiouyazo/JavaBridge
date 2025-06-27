package com.chiou.javabridge;

import com.chiou.javabridge.Models.CommandSourceQuery;
import com.chiou.javabridge.Models.SuggestionProviderBase;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ClientSuggestionProvider extends SuggestionProviderBase implements SuggestionProvider<FabricClientCommandSource> {
    @Override
    public String GetPlatform() { return "CLIENT"; }
    @Override
    public String GetHandler() { return "COMMAND"; }

    private final String providerId;
    private final String clientId;

    private final Communicator _communicator;
    private final Consumer<CommandSourceQuery> _onSuggestionQuery;

    private final Map<String, Object> PendingSuggestions = new ConcurrentHashMap<>();

    public ClientSuggestionProvider(String providerId, String clientId, Communicator communicator,
                                    Consumer<CommandSourceQuery> onSuggestionQuery) {
        this.providerId = providerId;
        this.clientId = clientId;
        this._communicator = communicator;
        this._onSuggestionQuery = onSuggestionQuery;
    }

    @Override
    public void PutPendingSuggestion(String guid, ServerCommandSource source) {
        PendingSuggestions.put(guid, source);
    }

    @Override
    public void FinalizeSuggestion(String guid) {
        PendingSuggestions.remove(guid);
        _communicator.SendToHost(clientId, AssembleMessage(guid, "SUGGESTION_FINALIZE", providerId + ":OK"));
    }

    @Override
    public void HandleQuery(String guid, String contextId, String payload) {
        _onSuggestionQuery.accept(new CommandSourceQuery(guid, clientId, providerId, contextId, payload, PendingSuggestions));
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) throws CommandSyntaxException {
        String requestId = UUID.randomUUID().toString();

        String payload = builder.getRemaining();

        _communicator.SendToHost(clientId, AssembleMessage(requestId, "SUGGESTION_REQUEST", providerId + ":" + requestId + ":" + payload));

        // Await response asynchronously
        return _communicator.waitForResponseAsync(requestId, 50000).thenApply(suggestions -> {
            for (String suggestion : suggestions.toString().split(",")) {
                builder.suggest(suggestion);
            }
            return builder.build();
        });
    }
}