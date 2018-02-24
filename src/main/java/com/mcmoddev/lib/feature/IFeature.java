package com.mcmoddev.lib.feature;

import javax.annotation.Nullable;
import com.mcmoddev.lib.capability.ICapabilitiesContainer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

public interface IFeature extends INBTSerializable<NBTTagCompound> {
    String getKey();

    @Nullable
    IFeatureHolder getHolder();
    void setHolder(@Nullable IFeatureHolder holder);

    /**
     * In this method the feature should specify all the capabilities it exports or might export in the future. <br>
     * This method is only called once after a feature is added to a {@link IFeatureHolder}.
     * But only if the holder itself supports capabilities.
     * @param container The capabilities container that stores capability for this feature's {@link IFeatureHolder}.
     */
    default void initCapabilities(final ICapabilitiesContainer container) { }
}
