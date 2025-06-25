package com.chiou.javabridge;

import com.chiou.javabridge.Models.CommandHandler;
import com.chiou.javabridge.Models.CommandNode;
import com.chiou.javabridge.Models.EventHandler;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

import java.util.function.Function;

public class ServerItemHandler extends EventHandler {
    @Override
    public String GetPlatform() { return "SERVER"; }

    @Override
    public String GetHandler() { return "ITEM"; }

    public Item SUSPICIOUS_SUBSTANCE;

    private final Logger _logger = JavaBridge.LOGGER;

    private final Communicator _communicator;

    public ServerItemHandler(Communicator communicator) {
        this._communicator = communicator;
    }

    public Item register(String name, Function<Item.Settings, Item> itemFactory, Item.Settings settings) {
        // Create the item key.
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(JavaBridge.MOD_ID, name));

        // Create the item instance.
        Item item = itemFactory.apply(settings.registryKey(itemKey));

        // Register the item.
        Registry.register(Registries.ITEM, itemKey, item);

        return item;
    }

    public void initialize() {
        SUSPICIOUS_SUBSTANCE = register("suspicious_substance", Item::new, new Item.Settings());
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS)
                .register((itemGroup) -> itemGroup.add(SUSPICIOUS_SUBSTANCE));
    }

    public void HandleRequest(String clientId, String guid, String platform, String event, String payload) {
        switch (event) {
            case "REGISTER_ITEM" -> handleRegisterCommand(clientId, guid, payload);

            default -> _logger.info("Unknown event: " + event);
        }
    }

    private void handleRegisterCommand(String clientId, String guid, String payload) {
        String[] split = payload.split("\\|", 3);
        String itemName = split.length > 0 ? split[0] : "";
        String itemDefinition = split.length > 1 ? split[1] : "";
        String newPayload = split.length > 2 ? split[2] : "";

        register(itemDefinition, Item::new, new Item.Settings());

        _communicator.SendToHost(clientId, AssembleMessage(guid, "ITEM_REGISTERED", payload));
    }
}
