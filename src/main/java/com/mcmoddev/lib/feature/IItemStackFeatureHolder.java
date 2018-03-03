package com.mcmoddev.lib.feature;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Interface implemented by {@link Item items} that can act as feature holders for item stacks.
 */
public interface IItemStackFeatureHolder {
    /**
     * Creates an {@link IFeatureHolder} used to manipulate the specified item stack.
     * @param stack The item stack to create a feature holder for.
     * @return The feature holder that can be used to manipulate the specified item stack.
     */
    default IFeatureHolder getFeatureHolder(final ItemStack stack) {
        final IFeatureHolder holder = new BaseItemStackFeatureHolder(stack);
        this.initFeatures(holder);
        return holder;
    }

    /**
     * Initializes features in the specified holder.
     * @param holder Holder containing all the features to be initialized.
     */
    void initFeatures(IFeatureHolder holder);
}
