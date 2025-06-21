package com.chiou.javabridge.Models;

public interface IRequirementChecker {
    public boolean check(String clientId, Object source, String commandName);
}
