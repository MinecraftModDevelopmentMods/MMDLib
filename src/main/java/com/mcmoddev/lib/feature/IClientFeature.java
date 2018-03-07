package com.mcmoddev.lib.feature;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Represents a feature that handles update data on client side.
 */
public interface IClientFeature extends IFeature {
    /**
     * Should update the feature on client side.
     * @param tag The update tag received from server side.
     */
    default void handleUpdateTag(final NBTTagCompound tag) {
        this.deserializeNBT(tag);
    }
}
