package com.chiou.javabridge;

import com.chiou.javabridge.Models.CommandArg;
import com.chiou.javabridge.Models.CommandRegistrationProxy;
import com.chiou.javabridge.Models.IRequirementChecker;

import java.util.ArrayList;
import java.util.List;

public class DynamicCommandRegistrar {
    private final CommandRegistrationProxy _registrar;

    public DynamicCommandRegistrar(CommandRegistrationProxy registrationProxy) {
        _registrar = registrationProxy;
    }

    public void registerCommand(String commandDefinition, IRequirementChecker requirementChecker) {
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
        _registrar.register(commandName, args, requirementChecker);

//        LiteralArgumentBuilder<ServerCommandSource> commandBuilder = CommandManager.literal(commandName);
//
//        commandBuilder.requires(source -> requirementChecker.check(source, commandName));
//
//        _registrar.buildArguments(commandBuilder, args, 0, commandName);
//
//        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
//            dispatcher.register(commandBuilder);
//        });
    }
}
