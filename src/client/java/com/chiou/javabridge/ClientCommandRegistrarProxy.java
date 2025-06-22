package com.chiou.javabridge;

import com.chiou.javabridge.Models.*;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.apache.commons.lang3.function.TriConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ClientCommandRegistrarProxy extends CommandRegistrationProxy {
    private final CommandHandler _commandHandler;
    private final Communicator _communicator;

    private final Map<String, SuggestionProviderBase> _suggestionProviders = new ConcurrentHashMap<>();
    private final Consumer<CommandSourceQuery> _onSuggestionQuery;

    public ClientCommandRegistrarProxy(CommandHandler handler, Communicator communicator, TriConsumer<String, String, String> onCommandExecuted,
                                       Consumer<CommandSourceQuery> onSuggestionQuery) {
        super(onCommandExecuted);
        _commandHandler = handler;
        this._communicator = communicator;
        this._onSuggestionQuery = onSuggestionQuery;
    }

    @Override
    public SuggestionProviderBase GetProvider(String providerId) {
        return _suggestionProviders.get(providerId);
    }

    @Override
    public void register(String clientId, CommandNode rootCommand) {
        LiteralArgumentBuilder<FabricClientCommandSource> commandBuilder = ClientCommandManager.literal(rootCommand.Name);
        commandBuilder.requires(source -> rootCommand.requirementChecker.check(clientId, source, rootCommand.Name));

        // parentPath needs to be empty here, otherwise itll put it at the front twice, like: screen:screen:new
        buildCommandTree(commandBuilder, rootCommand, clientId, "");

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(commandBuilder);
        });
    }

    private void buildCommandTree(ArgumentBuilder<FabricClientCommandSource, ?> builder, CommandNode node, String clientId, String parentPath) {
        String currentPath = parentPath.isEmpty() ? node.Name : parentPath + ":" + node.Name;

        // Build arguments for this node (if any)
        buildArguments(builder, node.args, 0, currentPath, clientId);

        // Recursively add subcommands
        for (CommandNode sub : node.subCommands) {
            LiteralArgumentBuilder<FabricClientCommandSource> subBuilder = ClientCommandManager.literal(sub.Name);

            String subPath = currentPath + ":" + sub.Name;

            subBuilder.requires(source -> sub.requirementChecker.check(clientId, source, subPath));
            buildCommandTree(subBuilder, sub, clientId, currentPath);
            builder.then(subBuilder);
        }
    }

    public void buildArguments(ArgumentBuilder<FabricClientCommandSource, ?> builder, List<CommandArg> args, int index, String fullCommandPath, String clientId) {
        if (index >= args.size()) {
            builder.executes(ctx -> {
                String payload = buildArgsPayload(ctx, args);
                String guid = UUID.randomUUID().toString();
                _commandHandler.PutPendingCommand(guid, ctx.getSource());
                _onCommandExecuted.accept(guid, fullCommandPath, payload);
                return 1;
            });
            return;
        }

        CommandArg arg = args.get(index);
        RequiredArgumentBuilder<FabricClientCommandSource, ?> argBuilder = ClientCommandManager.argument(arg.name, parseArgumentType(arg.type));

        if (arg.suggestionProvider != null) {
            if (arg.suggestionProvider.startsWith("CUSTOM:")) {
                String providerId = arg.suggestionProvider.substring("CUSTOM:".length());
                ClientSuggestionProvider suggestionProvider = new ClientSuggestionProvider(providerId, clientId, _communicator, _onSuggestionQuery);

                _suggestionProviders.put(providerId, suggestionProvider);
                argBuilder.suggests(suggestionProvider);
            }
            else {
                JavaBridge.LOGGER.warn("Generic suggestion provider " + arg.suggestionProvider + " is not available in client only commands.");
            }
        }

        if (arg.optional) {
            builder.executes(ctx -> {
                String payload = buildArgsPayload(ctx, args.subList(0, index));
                String guid = UUID.randomUUID().toString();
                _commandHandler.PutPendingCommand(guid, ctx.getSource());
                _onCommandExecuted.accept(guid, fullCommandPath, payload);
                return 1;
            });
        }

        buildArguments(argBuilder, args, index + 1, fullCommandPath, clientId);

        builder.then(argBuilder);
    }

    private String buildArgsPayload(CommandContext<FabricClientCommandSource> ctx, List<CommandArg> args) {
        List<String> argPairs = new ArrayList<>();
        for (CommandArg arg : args) {
            try {
                Object val = ctx.getArgument(arg.name, getJavaClassForType(arg.type));
                argPairs.add(arg.name + "=" + val.toString());
            } catch (IllegalArgumentException e) {
                // argument not present, ignore if optional
            }
        }
        return String.join("||", argPairs);
    }
}