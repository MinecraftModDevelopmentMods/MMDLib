package com.mcmoddev.lib.feature;

import java.awt.Color;
import javax.annotation.Nullable;
import net.minecraft.block.material.MapColor;
import net.minecraft.nbt.NBTTagCompound;

@SuppressWarnings("WeakerAccess")
public abstract class BaseFeature implements IFeature, IServerFeature {
    private final String key;
    private boolean dirty = false;
    private IFeatureHolder holder = null;

    protected BaseFeature(final String key) {
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
        this.setDirty(FeatureDirtyLevel.MIN_LEVEL);
    }

    protected void setDirty(final FeatureDirtyLevel level) {
        this.dirty = true;
        if (this.holder != null) {
            this.holder.featureChanged(this, level);
        }
    }

    @Override
    public final NBTTagCompound serializeNBT() {
        final NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return nbt;
    }

    protected abstract void writeToNBT(NBTTagCompound tag);

    @Override
    public NBTTagCompound getGuiUpdateTag(final boolean resetDirtyFlag) {
        final NBTTagCompound nbt = this.serializeNBT();

        if (resetDirtyFlag) {
            this.dirty = false;
        }
        return nbt;
    }

    @Nullable
    @Override
    public NBTTagCompound getTickUpdateTag(final boolean resetDirtyFlag) {
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
    public void setHolder(@Nullable final IFeatureHolder holder) {
        this.holder = holder;
    }

    public ISidedFeature getSidedWrapper(final Color color, final int priorityIndex) {
        return this.getSidedWrapper(color.getRGB(), priorityIndex);
    }

    public ISidedFeature getSidedWrapper(final MapColor color, final int priorityIndex) {
        return this.getSidedWrapper(color.colorValue, priorityIndex);
    }

    public ISidedFeature getSidedWrapper(final int color, final int priorityIndex) {
        return new SidedFeatureWrapper(this, color, priorityIndex);
    }
}
