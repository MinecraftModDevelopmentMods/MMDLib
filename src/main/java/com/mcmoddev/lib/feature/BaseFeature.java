package com.mcmoddev.lib.feature;

import java.awt.Color;
import javax.annotation.Nullable;
import net.minecraft.block.material.MapColor;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Base implementation of a server side only feature.
 */
public abstract class BaseFeature implements IFeature, IServerFeature {
    private final String key;
    private boolean dirty = false;
    private IFeatureHolder holder = null;

    /**
     * Initializes a new instance of BaseFeature.
     * @param key This feature's key.
     */
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

    /**
     * Sets this feature as dirty with the {@link FeatureDirtyLevel#MIN_LEVEL} level.
     */
    protected void setDirty() {
        this.setDirty(FeatureDirtyLevel.MIN_LEVEL);
    }

    /**
     * Sets this feature as dirty.
     * @param level The level of dirtiness.
     */
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

    /**
     * Writes this entire feature's data to nbt.
     * @param tag The tag that will hold this feature's data.
     */
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

    /**
     * Returns a {@link ISidedFeature sided feature wrapper} of this feature.
     * @param color The color of the sided feature. Used by the side configurator to visually identify this feature.
     * @param priorityIndex The index of the sided feature. Used by the side configurator for sorting purposes.
     * @return The sided feature wrapper containing this feature.
     */
    public ISidedFeature getSidedWrapper(final Color color, final int priorityIndex) {
        return this.getSidedWrapper(color.getRGB(), priorityIndex);
    }

    /**
     * Returns a {@link ISidedFeature sided feature wrapper} of this feature.
     * @param color The color of the sided feature. Used by the side configurator to visually identify this feature.
     * @param priorityIndex The index of the sided feature. Used by the side configurator for sorting purposes.
     * @return The sided feature wrapper containing this feature.
     */
    public ISidedFeature getSidedWrapper(final MapColor color, final int priorityIndex) {
        return this.getSidedWrapper(color.colorValue, priorityIndex);
    }

    /**
     * Returns a {@link ISidedFeature sided feature wrapper} of this feature.
     * @param color The color of the sided feature. Used by the side configurator to visually identify this feature.
     * @param priorityIndex The index of the sided feature. Used by the side configurator for sorting purposes.
     * @return The sided feature wrapper containing this feature.
     */
    public ISidedFeature getSidedWrapper(final int color, final int priorityIndex) {
        return new SidedFeatureWrapper(this, color, priorityIndex);
    }
}
