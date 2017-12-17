package com.mcmoddev.lib.feature;

import net.minecraft.nbt.NBTTagCompound;

public interface IClientFeature extends IFeature {
    /**
     * Should update the feature on client side.
     * @param tag The update tag received from server side.
     */
    default void handleUpdateTag(NBTTagCompound tag) {
        this.deserializeNBT(tag);
    }
}
