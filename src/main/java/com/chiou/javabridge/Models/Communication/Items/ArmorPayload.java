package com.chiou.javabridge.Models.Communication.Items;

import com.google.common.collect.Maps;
import net.minecraft.item.Item;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.Map;

public class ArmorPayload {
    public int Durability;
    public DefenseMap Defense;
    public int EnchantmentValue;
    public String EquipSound;
    public float Toughness;
    public float KnockbackResistance;
    public String RepairIngredient;
    public String AssetId;

    public int ArmorType;

    public TagKey<Item> GetRepairIngredient() {
        return TagKey.of(RegistryKeys.ITEM, Identifier.ofVanilla(RepairIngredient));
    }

    public Map<EquipmentType, Integer> GetDefenseMap() {
        return Maps.newEnumMap(
                Map.of(
                        EquipmentType.BOOTS,
                        Defense.BootsDefense,
                        EquipmentType.LEGGINGS,
                        Defense.LeggingsDefense,
                        EquipmentType.CHESTPLATE,
                        Defense.ChestplateDefense,
                        EquipmentType.HELMET,
                        Defense.HelmetDefense,
                        EquipmentType.BODY,
                        Defense.BodyDefense
                )
        );
    }

    public EquipmentType GetArmorType() {
        if(ArmorType == 0) {
            return EquipmentType.HELMET;
        } else if(ArmorType == 1) {
            return EquipmentType.CHESTPLATE;
        } else if(ArmorType == 2) {
            return EquipmentType.LEGGINGS;
        } else if(ArmorType == 3) {
            return EquipmentType.BOOTS;
        }

        return EquipmentType.BODY;
    }
}
