package com.chiou.javabridge;

import com.chiou.javabridge.Models.CommandArg;
import com.chiou.javabridge.Models.CommandNode;
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
        CommandNode root = ParseCommandNode(commandDefinition, requirementChecker);
        _registrar.register(clientId, root);
    }
    public void registerCommand(String clientId, CommandNode node) {
        _registrar.register(clientId, node);
    }

    public CommandNode ParseCommandNode(String commandDefinition, IRequirementChecker requirementChecker) {
        int firstPipe = commandDefinition.indexOf('|');
        String commandName = firstPipe >= 0 ? commandDefinition.substring(0, firstPipe).trim() : commandDefinition.trim();
        String rest = firstPipe >= 0 ? commandDefinition.substring(firstPipe + 1).trim() : "";

        List<CommandArg> args = new ArrayList<>();
        List<CommandNode> subCommands = new ArrayList<>();

        if (!rest.isEmpty()) {
            int subcommandsIndex = rest.indexOf("subcommands[");
            String argsPart;
            String subsPart = null;

            if (subcommandsIndex >= 0) {
                argsPart = rest.substring(0, subcommandsIndex).trim();
                subsPart = rest.substring(subcommandsIndex).trim();
            } else {
                argsPart = rest;
            }

            // Parse arguments part
            if (!argsPart.isEmpty()) {
                String[] argDefs = argsPart.split(",");
                for (String argDef : argDefs) {
                    argDef = argDef.trim();
                    if (argDef.isEmpty()) continue;

                    String[] argParts = argDef.split("\\|");
                    String[] nameAndType = argParts[0].split(":");

                    String argName = nameAndType[0].trim();
                    String argType = nameAndType.length > 1 ? nameAndType[1].trim() : "string";

                    boolean optional = false;
                    String suggestionProvider = null;

                    for (int i = 1; i < argParts.length; i++) {
                        String opt = argParts[i].trim();
                        if (opt.equalsIgnoreCase("optional")) {
                            optional = true;
                        } else if (opt.toLowerCase().startsWith("suggestion=")) {
                            suggestionProvider = opt.substring("suggestion=".length()).trim();
                        }
                    }

                    args.add(new CommandArg(argName, argType, optional, suggestionProvider));
                }
            }

            // Parse subcommands part recursively
            if (subsPart != null && subsPart.startsWith("subcommands[")) {
                String inner = extractBracketContent(subsPart, "subcommands[");
                List<String> subCommandDefs = splitTopLevelCommands(inner);
                for (String subDef : subCommandDefs) {
                    CommandNode subNode = ParseCommandNode(subDef, requirementChecker);
                    subCommands.add(subNode);
                }
            }
        }

        CommandNode node = new CommandNode(commandName, args, requirementChecker);
        node.subCommands.addAll(subCommands);
        return node;
    }

    private String extractBracketContent(String s, String prefix) {
        int start = s.indexOf(prefix) + prefix.length();
        int bracketCount = 1;
        int end = start;
        while (end < s.length() && bracketCount > 0) {
            char c = s.charAt(end);
            if (c == '[') bracketCount++;
            else if (c == ']') bracketCount--;
            end++;
        }
        return s.substring(start, end - 1);
    }

    private List<String> splitTopLevelCommands(String s) {
        List<String> results = new ArrayList<>();
        int bracketCount = 0;
        int lastSplit = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[') bracketCount++;
            else if (c == ']') bracketCount--;
            else if (c == ';' && bracketCount == 0) {
                results.add(s.substring(lastSplit, i).trim());
                lastSplit = i + 1;
            }
        }
        if (lastSplit < s.length()) {
            results.add(s.substring(lastSplit).trim());
        }
        return results;
    }
}
