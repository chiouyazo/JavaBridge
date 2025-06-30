package com.chiou.javabridge.Models.Communication.Items;

import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class ItemRegistration {
    public String ItemDefinition;
    public int ItemType;
    public Object ItemPayload;
    public String ItemGroup;


    public int MaxCount;
    public int MaxDamage;

    public String JukeboxSong;
    public String RepairableItem;

    public Boolean Enchantable;
    public Boolean Fireproof;
    public int Rarity;
    public int EquipableSlot;
    public int EquipableUnswappable;
    public FoodPayload Food;

    public RegistryKey<JukeboxSong> GetJukeboxSong() {
        if(JukeboxSong == null)
            return null;
        return RegistryKey.of(RegistryKeys.JUKEBOX_SONG, Identifier.ofVanilla(JukeboxSong));
    }

    public Item GetRepairItem() {
        if(RepairableItem == null)
            return null;
        return Registries.ITEM.get(Identifier.ofVanilla(RepairableItem));
    }

    public ItemType GetItemType() {
        if(ItemType == 0)
            return com.chiou.javabridge.Models.Communication.Items.ItemType.Armor;

        return null;
    }

    public Rarity GetRarity() {
        if (Rarity == 0)
            return net.minecraft.util.Rarity.COMMON;
        if (Rarity == 1)
            return net.minecraft.util.Rarity.UNCOMMON;
        if (Rarity == 2)
            return net.minecraft.util.Rarity.RARE;
        if (Rarity == 3)
            return net.minecraft.util.Rarity.EPIC;

        return null;
    }

    public EquipmentSlot GetEquippableSlot() {
        return ParseEquipmentSlot(EquipableSlot);
    }

    public EquipmentSlot GetEquippableUnswappableSlot() {
        return ParseEquipmentSlot(EquipableUnswappable);
    }

    private EquipmentSlot ParseEquipmentSlot(int value) {
        if (value == 0)
            return EquipmentSlot.MAINHAND;
        if (value == 1)
            return EquipmentSlot.OFFHAND;
        if (value == 2)
            return EquipmentSlot.FEET;
        if (value == 3)
            return EquipmentSlot.LEGS;
        if (value == 4)
            return EquipmentSlot.CHEST;
        if (value == 5)
            return EquipmentSlot.HEAD;
        if (value == 6)
            return EquipmentSlot.BODY;
        if (value == 7)
            return EquipmentSlot.SADDLE;

        return null;
    }
}
