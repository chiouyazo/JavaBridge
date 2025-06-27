package com.chiou.javabridge.Handlers;

import com.chiou.javabridge.Communicator;
import com.chiou.javabridge.JavaBridge;
import com.chiou.javabridge.Models.EventHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class ServerItemHandler extends EventHandler {
    @Override
    public String GetPlatform() { return "SERVER"; }

    @Override
    public String GetHandler() { return "ITEM"; }

    private final Logger _logger = JavaBridge.LOGGER;

    private final Communicator _communicator;

    private final CompletableFuture<String> _isReady = new CompletableFuture<>();

    public ServerItemHandler(Communicator communicator) {
        this._communicator = communicator;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> _isReady.complete(""));
    }

    public Boolean register(String name, Function<Item.Settings, Item> itemFactory, Item.Settings settings, @Nullable RegistryKey<ItemGroup> attachedItemGroup) {
        try {
//            _isReady.get();
            RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of("java-bridge", name));

            Item item = itemFactory.apply(settings.registryKey(itemKey));

            Registry.register(Registries.ITEM, itemKey, item);

            if (attachedItemGroup != null) {
                ItemGroupEvents.modifyEntriesEvent(attachedItemGroup).register((itemGroup) -> itemGroup.add(item));
            }

            return true;
        }
        catch (Exception ex) {
            JavaBridge.LOGGER.error("Could not register item.", ex);
            return false;
        }
    }

    public void HandleRequest(String clientId, String guid, String platform, String event, String payload) {
        switch (event) {
            case "REGISTER_ITEM" -> RegisterItem(clientId, guid, payload);

            default -> _logger.info("Unknown event: " + event);
        }
    }

    private void RegisterItem(String clientId, String guid, String payload) {
        String[] split = payload.split("\\|", 2);
        String itemDefinition = split.length > 0 ? split[0] : "";
        String newPayload = split.length > 1 ? split[1] : "";

        RegistryKey<ItemGroup> itemGroup = ParseItemGroup(newPayload);

        Boolean result = register(itemDefinition, Item::new, new Item.Settings(), itemGroup);

        _communicator.SendToHost(clientId, AssembleMessage(guid, "ITEM_REGISTERED", result.toString()));
    }

    private RegistryKey<ItemGroup> ParseItemGroup(String newPayload) {
        switch (newPayload) {
            case "BUILDING_BLOCKS" -> {
                return ItemGroups.BUILDING_BLOCKS;
            }
            case "COLORED_BLOCKS" -> {
                return ItemGroups.COLORED_BLOCKS;
            }
            case "NATURAL" -> {
                return ItemGroups.NATURAL;
            }
            case "FUNCTIONAL" -> {
                return ItemGroups.FUNCTIONAL;
            }
            case "REDSTONE" -> {
                return ItemGroups.REDSTONE;
            }
            case "HOTBAR" -> {
                return ItemGroups.HOTBAR;
            }
            case "SEARCH" -> {
                return ItemGroups.SEARCH;
            }
            case "TOOLS" -> {
                return ItemGroups.TOOLS;
            }
            case "COMBAT" -> {
                return ItemGroups.COMBAT;
            }
            case "FOOD_AND_DRINK" -> {
                return ItemGroups.FOOD_AND_DRINK;
            }
            case "INGREDIENTS" -> {
                return ItemGroups.INGREDIENTS;
            }
            case "SPAWN_EGGS" -> {
                return ItemGroups.SPAWN_EGGS;
            }
            case "OPERATOR" -> {
                return ItemGroups.OPERATOR;
            }
            case "INVENTORY" -> {
                return ItemGroups.INVENTORY;
            }
            default -> {
                return null;
            }
        }
    }
}
