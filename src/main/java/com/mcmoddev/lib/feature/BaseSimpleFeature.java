package com.mcmoddev.lib.feature;

import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;

public abstract class BaseSimpleFeature implements IFeature, IServerFeature {
    private String key;
    private NBTTagCompound dirtyTag;
    private IFeatureHolder holder = null;

    protected BaseSimpleFeature(String key) {
        this.key = key;
        this.dirtyTag = new NBTTagCompound();
    }

    @Override
    public String getKey() {
        return this.key;
    }

    @Override
    public boolean isDirty() {
        return (this.dirtyTag.getSize() > 0);
    }

    @Override
    public NBTTagCompound getUpdateTag(boolean resetDirtyFlag) {
        NBTTagCompound tag = this.dirtyTag;
        if (resetDirtyFlag) {
            this.dirtyTag = new NBTTagCompound();
        }

        return tag;
    }

    @Override
    public final NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return nbt;
    }

    protected abstract void writeToNBT(NBTTagCompound tag);

    @Nullable
    @Override
    public IFeatureHolder getHolder() {
        return this.holder;
    }

    @Override
    public void setHolder(@Nullable IFeatureHolder holder) {
        this.holder = holder;
    }
}
