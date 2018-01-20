package com.mcmoddev.lib.feature;

import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;

public abstract class BaseFeature implements IFeature, IServerFeature {
    private final String key;
    private boolean dirty = false;
    private IFeatureHolder holder = null;

    protected BaseFeature(String key) {
        this.key = key;
    }

    @Override
    public String getKey() {
        return this.key;
    }

    @Override
    public boolean isDirty() {
        return this.dirty;
    }

    protected void setDirty() {
        this.dirty = true;
        if (this.holder != null) {
            this.holder.featuredChanged(this);
        }
    }

    @Override
    public final NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return nbt;
    }

    protected abstract void writeToNBT(NBTTagCompound tag);

    @Override
    public NBTTagCompound getGuiUpdateTag(boolean resetDirtyFlag) {
        NBTTagCompound nbt = this.serializeNBT();

        if (resetDirtyFlag) {
            this.dirty = false;
        }
        return nbt;
    }

    @Nullable
    @Override
    public NBTTagCompound getTickUpdateTag(boolean resetDirtyFlag) {
        return null;
    }

    @Nullable
    @Override
    public NBTTagCompound getLoadUpdateTag() {
        return this.serializeNBT();
    }

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