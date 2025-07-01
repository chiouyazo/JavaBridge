package com.chiou.javabridge.Handlers;

import com.chiou.javabridge.Communicator;
import com.chiou.javabridge.JavaBridge;
import com.chiou.javabridge.Models.Communication.Items.ArmorPayload;
import com.chiou.javabridge.Models.Communication.Items.ItemRegistration;
import com.chiou.javabridge.Models.Communication.Items.ItemType;
import com.chiou.javabridge.Models.Communication.MessageBase;
import com.chiou.javabridge.Models.EventHandler;
import com.google.common.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

        Boolean result = BuildItem(itemRegistration);

        if (!result)
            JavaBridge.LOGGER.info("Failed to register item: {}", itemRegistration.ItemDefinition);

        _communicator.SendToHost(clientId, AssembleMessage(message.Id, "ITEM_REGISTERED", result.toString()));
    }

    public void BuildBakedItems(Path path) {
        try {
            Path bakedPath = path.resolve("baked");

            if (!Files.exists(bakedPath))
                return;

            List<String> failedItems = new ArrayList<>();
            Path bakedItems = bakedPath.resolve("items.json");

            if (!Files.exists(bakedItems))
                return;

            List<ItemRegistration> items = new ArrayList<>();
            Type itemListType = new TypeToken<List<ItemRegistration>>() {}.getType();
            items = JavaBridge.Gson.<List<ItemRegistration>>fromJson(Files.readString(bakedItems), itemListType);

            if (!items.isEmpty()) {
                for (ItemRegistration item : items) {
                    Boolean result = BuildItem(item);

                    if (!result) {
                        failedItems.add(item.ItemDefinition);
                        JavaBridge.LOGGER.info("Failed to register baked item: {}", item.ItemDefinition);
                    }
                }
            }

            int successfulRegistrations = items.size() - failedItems.size();

            JavaBridge.LOGGER.info("Registered {} out of {} baked items", successfulRegistrations, items.size());

            if (!failedItems.isEmpty()) {
                JavaBridge.LOGGER.info("Failed items: {}", String.join(", ", failedItems));
            }
        } catch (Exception e) {
            JavaBridge.LOGGER.error("Failed to build baked items.", e);
        }
    }

    public Boolean BuildItem(ItemRegistration itemRegistration) {
        Boolean result = false;
        if (itemRegistration.GetItemType() == ItemType.Armor) {
            result = BuildArmor(itemRegistration);
        }

        return result;
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

        itemSettings.maxCount(itemRegistration.MaxCount);
        itemSettings.maxDamage(itemRegistration.MaxDamage);

        // TODO: Add the onConsume event here for e.g. effects later
        if (itemRegistration.Food != null)
            itemSettings.food(itemRegistration.Food.BuildFoodComponent());

        if (itemRegistration.Enchantable)
            itemSettings.enchantable(1);

        if (itemRegistration.Fireproof)
            itemSettings.fireproof();


        Rarity rarity = itemRegistration.GetRarity();
        if (rarity != null)
            itemSettings.rarity(rarity);

        EquipmentSlot equippableSlot = itemRegistration.GetEquippableSlot();
        if (equippableSlot != null)
            itemSettings.equippable(equippableSlot);

        EquipmentSlot equippableUnswappableSlot = itemRegistration.GetEquippableUnswappableSlot();
        if (equippableUnswappableSlot != null)
            itemSettings.equippableUnswappable(equippableUnswappableSlot);

        RegistryKey<JukeboxSong> jukeboxSong = itemRegistration.GetJukeboxSong();
        if (jukeboxSong != null)
            itemSettings.jukeboxPlayable(jukeboxSong);

        Item repairItem = itemRegistration.GetRepairItem();
        if (repairItem != null)
            itemSettings.repairable(repairItem);


        // tool
        // pickaxe
        // axe
        // hoe
        // shovel
        // sword
        // armor
        // wolfArmor
        // horseArmor


        return register(itemRegistration.ItemDefinition, Item::new, itemSettings, itemGroup);
    }

    private RegistryKey<ItemGroup> ParseItemGroup(String newPayload) {
        ItemGroup group = Registries.ITEM_GROUP.get(Identifier.ofVanilla(newPayload));
        Optional<RegistryKey<ItemGroup>> key = Registries.ITEM_GROUP.getKey(group);

//        ItemGroups.NATURAL
        return key.orElse(null);
    }
}
