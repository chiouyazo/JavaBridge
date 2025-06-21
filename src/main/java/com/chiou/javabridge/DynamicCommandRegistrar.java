package com.chiou.javabridge;

import com.mojang.brigadier.arguments.*;
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

public class DynamicCommandRegistrar {
    private final Boolean _isClient;
    private final TriConsumer<String, String, String> onCommandExecuted;
    private final CommandHandler _commandHandler;

    public DynamicCommandRegistrar(Boolean isClient, CommandHandler commandHandler, TriConsumer<String, String, String> onCommandExecuted) {
        _isClient = isClient;
        _commandHandler = commandHandler;
        this.onCommandExecuted = onCommandExecuted;
    }

    public void registerCommand(String commandDefinition, BridgeRequirementChecker requirementChecker) {
        String[] parts = commandDefinition.split("\\|", 2);
        String commandName = parts[0];
        List<CommandArg> args = new ArrayList<>();

        if (parts.length > 1 && !parts[1].isEmpty()) {
            String[] argsSplit = parts[1].split(",");
            for (String argDef : argsSplit) {
                String[] argParts = argDef.split("\\|");
                String[] nameAndType = argParts[0].split(":");
                String argName = nameAndType[0];
                String argType = nameAndType.length > 1 ? nameAndType[1] : "string";
                boolean optional = argParts.length > 1 && "optional".equalsIgnoreCase(argParts[1]);
                args.add(new CommandArg(argName, argType, optional));
            }
        }

        LiteralArgumentBuilder<ServerCommandSource> commandBuilder = CommandManager.literal(commandName);

        commandBuilder.requires(source -> requirementChecker.check(source, commandName) && (!_isClient || source.isExecutedByPlayer()));

        buildArguments(commandBuilder, args, 0, commandName);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(commandBuilder);
        });
    }

    private void buildArguments(ArgumentBuilder<ServerCommandSource, ?> builder, List<CommandArg> args, int index, String commandName) {
        if (index >= args.size()) {
            builder.executes(ctx -> {
                String payload = buildArgsPayload(ctx, args);
                String guid = UUID.randomUUID().toString();
                _commandHandler.PendingCommands.put(guid, ctx.getSource());
                onCommandExecuted.accept(guid, commandName, payload);
//                ctx.getSource().isExecutedByPlayer()
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
                _commandHandler.PendingCommands.put(guid, ctx.getSource());
                onCommandExecuted.accept(guid, commandName, payload);
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

    private static class CommandArg {
        String name;
        String type;
        boolean optional;

        CommandArg(String name, String type, boolean optional) {
            this.name = name;
            this.type = type;
            this.optional = optional;
        }
    }

    private ArgumentType<?> parseArgumentType(String type) {
        return switch (type.toLowerCase()) {
            case "int", "integer" -> IntegerArgumentType.integer();
            case "long" -> LongArgumentType.longArg();
            case "float" -> FloatArgumentType.floatArg();
            case "double" -> DoubleArgumentType.doubleArg();
            case "bool", "boolean" -> BoolArgumentType.bool();
            case "string" -> StringArgumentType.word();
            default -> StringArgumentType.word(); // fallback
        };
    }

    private Class<?> getJavaClassForType(String type) {
        return switch (type.toLowerCase()) {
            case "int", "integer" -> Integer.class;
            case "long" -> Long.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            case "bool", "boolean" -> Boolean.class;
            case "string" -> String.class;
            default -> String.class;
        };
    }
}
