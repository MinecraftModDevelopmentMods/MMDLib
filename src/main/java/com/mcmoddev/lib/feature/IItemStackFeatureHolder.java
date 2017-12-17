package com.mcmoddev.lib.feature;

import net.minecraft.item.ItemStack;

public interface IItemStackFeatureHolder {
    default IFeatureHolder getFeatureHolder(ItemStack stack) {
        IFeatureHolder holder = new BaseItemStackFeatureHolder(stack);
        this.initFeatures(holder);
        return holder;
    }

    void initFeatures(IFeatureHolder holder);
}
