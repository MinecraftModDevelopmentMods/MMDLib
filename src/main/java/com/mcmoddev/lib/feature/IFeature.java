package com.mcmoddev.lib.feature;

import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

public interface IFeature extends INBTSerializable<NBTTagCompound> {
    String getKey();

    @Nullable
    IFeatureHolder getHolder();
    void setHolder(@Nullable IFeatureHolder holder);
}
