package com.chiou.javabridge.Models;

public class CommandArg {
    public String name;
    public String type;
    public boolean optional;

    public String suggestionProvider;

    public CommandArg(String name, String type, boolean optional, String suggestionProvider) {
        this.name = name;
        this.type = type;
        this.optional = optional;
        this.suggestionProvider = suggestionProvider;
    }
}