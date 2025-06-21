package com.chiou.javabridge.Models;

public class CommandArg {
    public String name;
    public String type;
    public boolean optional;

    public CommandArg(String name, String type, boolean optional) {
        this.name = name;
        this.type = type;
        this.optional = optional;
    }
}