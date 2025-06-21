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

    public void registerCommand(String clientId, String commandDefinition, IRequirementChecker requirementChecker) {
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
        _registrar.register(clientId, commandName, args, requirementChecker);
    }
}
