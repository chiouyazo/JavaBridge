package com.chiou.javabridge.Models;

import com.chiou.javabridge.JavaBridgeSuggestionProvider;
import com.mojang.brigadier.arguments.*;
import org.apache.commons.lang3.function.TriConsumer;

import java.util.List;
import java.util.Map;

public abstract class CommandRegistrationProxy {
    public CommandRegistrationProxy(TriConsumer<String, String, String> onCommandExecuted) {
        _onCommandExecuted = onCommandExecuted;
    }

    protected TriConsumer<String, String, String> _onCommandExecuted;

    public abstract SuggestionProviderBase GetProvider(String providerId);

    public abstract void register(String clientId, CommandNode rootCommand);

    protected ArgumentType<?> parseArgumentType(String type) {
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

    protected Class<?> getJavaClassForType(String type) {
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