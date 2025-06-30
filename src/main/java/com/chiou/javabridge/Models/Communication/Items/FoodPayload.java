package com.chiou.javabridge.Models.Communication.Items;

import net.minecraft.component.type.FoodComponent;

public class FoodPayload {
    public int Nutrition;
    public int Saturation;
    public Boolean CanAlwaysEat;

    public FoodComponent BuildFoodComponent() {
        return new FoodComponent(Nutrition, Saturation, CanAlwaysEat);
    }
}
