package com.chiou.javabridge.Models.Communication.Items;

public class ItemRegistration {
    public String ItemDefinition;
    public int ItemType;
    public Object ItemPayload;
    public String ItemGroup;

    public ItemType GetItemType() {
        if(ItemType == 0)
            return com.chiou.javabridge.Models.Communication.Items.ItemType.Armor;

        return null;
    }
}
