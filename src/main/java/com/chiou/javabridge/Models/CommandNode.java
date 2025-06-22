package com.chiou.javabridge.Models;

import java.util.ArrayList;
import java.util.List;

public class CommandNode {
    public String Name;
    public List<CommandArg> args = new ArrayList<>();
    public List<CommandNode> subCommands = new ArrayList<>();
    public IRequirementChecker requirementChecker;

    public CommandNode(String name, List<CommandArg> args, IRequirementChecker req) {
        this.Name = name;
        this.args = args;
        this.requirementChecker = req;
    }
}