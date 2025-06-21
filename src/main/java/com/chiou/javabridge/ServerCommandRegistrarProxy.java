package com.chiou.javabridge;

import com.chiou.javabridge.Models.CommandArg;
import com.chiou.javabridge.Models.CommandRegistrationProxy;
import com.chiou.javabridge.Models.CommandHandler;
import com.chiou.javabridge.Models.IRequirementChecker;
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
    public void register(String clientId, String commandName, List<CommandArg> args, IRequirementChecker requirementChecker) {
        LiteralArgumentBuilder<ServerCommandSource> commandBuilder = CommandManager.literal(commandName);
        commandBuilder.requires(source -> requirementChecker.check(clientId, source, commandName));

        buildArguments(commandBuilder, args, 0, commandName);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(commandBuilder);
        });
    }

    public void buildArguments(ArgumentBuilder<ServerCommandSource, ?> builder, List<CommandArg> args, int index, String commandName) {
        if (index >= args.size()) {
            builder.executes(ctx -> {
                String payload = buildArgsPayload(ctx, args);
                String guid = UUID.randomUUID().toString();
                _commandHandler.PutPendingCommand(guid, ctx.getSource());
                _onCommandExecuted.accept(guid, commandName, payload);
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
                _onCommandExecuted.accept(guid, commandName, payload);
                return 1;
            });
        }

        buildArguments(argBuilder, args, index + 1, commandName);

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