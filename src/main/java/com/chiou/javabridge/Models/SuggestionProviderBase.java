package com.chiou.javabridge.Models;

import net.minecraft.server.command.ServerCommandSource;

public abstract class SuggestionProviderBase extends EventHandler {
    public abstract void PutPendingSuggestion(String guid, ServerCommandSource source);
    public abstract void HandleQuery(String guid, String contextId, String payload);
    public abstract void FinalizeSuggestion(String guid);
}
