package com.chiou.javabridge.Handlers;

import com.chiou.javabridge.Communicator;
import com.chiou.javabridge.JavaBridge;
import com.chiou.javabridge.Models.Communication.Items.ArmorPayload;
import com.chiou.javabridge.Models.Communication.Items.ItemRegistration;
import com.chiou.javabridge.Models.Communication.Items.ItemType;
import com.chiou.javabridge.Models.Communication.MessageBase;
import com.chiou.javabridge.Models.EventHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static net.minecraft.item.equipment.EquipmentAssetKeys.REGISTRY_KEY;

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
            RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(".minecraft", name));

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

    public void HandleRequest(String clientId, MessageBase message) {
        switch (message.Event) {
            case "REGISTER_ITEM" -> RegisterItem(clientId, message);

            default -> _logger.info("Unknown event: " + message.Event);
        }
    }

    private void RegisterItem(String clientId, MessageBase message) {
        ItemRegistration itemRegistration = JavaBridge.Gson.fromJson(message.GetPayload(), ItemRegistration.class);

        Boolean result = false;
        if (itemRegistration.GetItemType() == ItemType.Armor) {
            result = BuildArmor(itemRegistration);
        }

//        new ArmorMaterial()
//        settings.armor()


//        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(soundId));

//        SoundEvent soundEvent = Registries.SOUND_EVENT.get(Identifier.ofVanilla("entity.allay.death"));
//
//        String[] split = message.GetPayload().split("\\|", 2);
//        String itemDefinition = split.length > 0 ? split[0] : "";
//        String newPayload = split.length > 1 ? split[1] : "";
//
//        RegistryKey<ItemGroup> itemGroup = ParseItemGroup(newPayload);
//
//        Boolean result = register(itemDefinition, Item::new, new Item.Settings(), itemGroup);

        _communicator.SendToHost(clientId, AssembleMessage(message.Id, "ITEM_REGISTERED", result.toString()));
    }

    private Boolean BuildArmor(ItemRegistration itemRegistration) {

        ArmorPayload armorPayload = JavaBridge.Gson.fromJson(itemRegistration.ItemPayload.toString(), ArmorPayload.class);
        RegistryKey<ItemGroup> itemGroup = ParseItemGroup(itemRegistration.ItemGroup);

        SoundEvent soundEvent = Registries.SOUND_EVENT.get(Identifier.ofVanilla(armorPayload.EquipSound));
        RegistryEntry<SoundEvent> soundEntry = Registries.SOUND_EVENT.getEntry(soundEvent);

        RegistryKey<EquipmentAsset> assetId = RegistryKey.of(REGISTRY_KEY, Identifier.ofVanilla(armorPayload.AssetId));

        ArmorMaterial armorMaterial = new ArmorMaterial(
                armorPayload.Durability,
                armorPayload.GetDefenseMap(),
                armorPayload.EnchantmentValue,
                soundEntry,
                armorPayload.Toughness,
                armorPayload.KnockbackResistance,
                armorPayload.GetRepairIngredient(),
                assetId);
        Item.Settings itemSettings = new Item.Settings().armor(armorMaterial, armorPayload.GetArmorType());

        return register(itemRegistration.ItemDefinition, Item::new, itemSettings, itemGroup);
    }

    private RegistryKey<ItemGroup> ParseItemGroup(String newPayload) {
        ItemGroup group = Registries.ITEM_GROUP.get(Identifier.ofVanilla(newPayload));
        Optional<RegistryKey<ItemGroup>> key = Registries.ITEM_GROUP.getKey(group);

//        ItemGroups.NATURAL
        return key.orElse(null);
    }
}
