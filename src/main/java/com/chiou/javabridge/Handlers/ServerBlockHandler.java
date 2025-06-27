package com.chiou.javabridge.Handlers;

import com.chiou.javabridge.Communicator;
import com.chiou.javabridge.JavaBridge;
import com.chiou.javabridge.Models.EventHandler;
import com.chiou.javabridge.Models.SoundMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class ServerBlockHandler extends EventHandler {
    @Override
    public String GetPlatform() { return "SERVER"; }

    @Override
    public String GetHandler() { return "BLOCK"; }

    private final Logger _logger = JavaBridge.LOGGER;

    private final Communicator _communicator;

    private final CompletableFuture<String> _isReady = new CompletableFuture<>();

    public ServerBlockHandler(Communicator communicator) {
        this._communicator = communicator;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> _isReady.complete(""));
    }

    public Boolean register(String name, Function<AbstractBlock.Settings, Block> blockFactory, AbstractBlock.Settings settings, boolean shouldRegisterItem, @Nullable RegistryKey<ItemGroup> attachedItemGroup) {
        try {
//            _isReady.get();
            RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of("java-bridge", name));

            Block block = blockFactory.apply(settings.registryKey(blockKey));

            if (shouldRegisterItem) {
                RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of("java-bridge", name));

                BlockItem blockItem = new BlockItem(block, new Item.Settings().registryKey(itemKey));
                Registry.register(Registries.ITEM, itemKey, blockItem);
            }

            Registry.register(Registries.BLOCK, blockKey, block);

            if (attachedItemGroup != null) {
                ItemGroupEvents.modifyEntriesEvent(attachedItemGroup).register((itemGroup) -> itemGroup.add(block));
            }

            return true;
        }
        catch (Exception ex) {
            JavaBridge.LOGGER.error("Could not register block.", ex);
            return false;
        }
    }


    public void HandleRequest(String clientId, String guid, String platform, String event, String payload) {
        switch (event) {
            case "REGISTER_BLOCK" -> RegisterBlock(clientId, guid, payload);

            default -> _logger.info("Unknown event: " + event);
        }
    }

    private void RegisterBlock(String clientId, String guid, String payload) {
        String[] split = payload.split("\\|", 2);
        String itemDefinition = split.length > 0 ? split[0] : "";
        String newPayload = split.length > 1 ? split[1] : "";

        String[] blockArgs = newPayload.split(";", 3);

        BlockSoundGroup itemSound = SoundMap.ParseItemSound(blockArgs.length > 0 ? blockArgs[0] : "");
        boolean shouldRegisterItem = Boolean.parseBoolean(blockArgs.length > 1 ? blockArgs[1] : "");
        RegistryKey<ItemGroup> itemGroup = ParseItemGroup(blockArgs.length > 2 ? blockArgs[2] : "");

        Boolean result = register(itemDefinition, Block::new, AbstractBlock.Settings.create().sounds(itemSound), shouldRegisterItem, itemGroup);

        _communicator.SendToHost(clientId, AssembleMessage(guid, "BLOCK_REGISTERED", result.toString()));
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
