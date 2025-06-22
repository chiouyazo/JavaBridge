package com.chiou.javabridge;

import com.chiou.javabridge.Models.*;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.apache.commons.lang3.function.TriConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ServerCommandRegistrarProxy extends CommandRegistrationProxy {
    private final CommandHandler _commandHandler;

    public ServerCommandRegistrarProxy(CommandHandler handler, TriConsumer<String, String, String> onCommandExecuted) {
        super(onCommandExecuted);
        _commandHandler = handler;
    }

    @Override
    public void register(String clientId, CommandNode rootCommand) {
        LiteralArgumentBuilder<ServerCommandSource> commandBuilder = CommandManager.literal(rootCommand.Name);
        commandBuilder.requires(source -> rootCommand.requirementChecker.check(clientId, source, rootCommand.Name));

        // parentPath needs to be empty here, otherwise itll put it at the front twice, like: screen:screen:new
        buildCommandTree(commandBuilder, rootCommand, clientId, "");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(commandBuilder);
        });
    }

    private void buildCommandTree(ArgumentBuilder<ServerCommandSource, ?> builder, CommandNode node, String clientId, String parentPath) {
        String currentPath = parentPath.isEmpty() ? node.Name : parentPath + ":" + node.Name;

        // Build arguments for this node (if any)
        buildArguments(builder, node.args, 0, currentPath);

        // Recursively add subcommands
        for (CommandNode sub : node.subCommands) {
            LiteralArgumentBuilder<ServerCommandSource> subBuilder = CommandManager.literal(sub.Name);

            String subPath = currentPath + ":" + sub.Name;

            subBuilder.requires(source -> sub.requirementChecker.check(clientId, source, subPath));
            buildCommandTree(subBuilder, sub, clientId, currentPath);
            builder.then(subBuilder);
        }
    }

    public void buildArguments(ArgumentBuilder<ServerCommandSource, ?> builder, List<CommandArg> args, int index, String fullCommandPath) {
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
        RequiredArgumentBuilder<ServerCommandSource, ?> argBuilder = CommandManager.argument(arg.name, parseArgumentType(arg.type));

        if (arg.optional) {
            builder.executes(ctx -> {
                String payload = buildArgsPayload(ctx, args.subList(0, index));
                String guid = UUID.randomUUID().toString();
                _commandHandler.PutPendingCommand(guid, ctx.getSource());
                _onCommandExecuted.accept(guid, fullCommandPath, payload);
                return 1;
            });
        }

        buildArguments(argBuilder, args, index + 1, fullCommandPath);

        builder.then(argBuilder);
    }

    private String buildArgsPayload(CommandContext<ServerCommandSource> ctx, List<CommandArg> args) {
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