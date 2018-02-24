package com.mcmoddev.lib.feature;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.mcmoddev.lib.capability.ICapabilitiesContainer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.Constants;

@SuppressWarnings("WeakerAccess")
public class SidedFeatureWrapper implements IFeature, IClientFeature, IServerFeature, ISidedFeature, ICapabilityProvider {
    private EnumFacing[] facings = null;
    private boolean dirty = false;

    private final IFeature feature;
    private final IClientFeature clientFeature;
    private final IServerFeature serverFeature;
    private final ICapabilityProvider capProvider;

    private final int color;
    private final int priorityIndex;

    public SidedFeatureWrapper(final IFeature feature, final int color, final int priorityIndex) {
        this.feature = feature;
        this.clientFeature = (feature instanceof IClientFeature) ? (IClientFeature)feature : null;
        this.serverFeature = (feature instanceof IServerFeature) ? (IServerFeature)feature : null;
        this.capProvider = (feature instanceof ICapabilityProvider) ? (ICapabilityProvider)feature : null;

        this.color = color;
        this.priorityIndex = priorityIndex;
    }

    public int getColor() {
        return this.color;
    }

    public int getPriorityIndex() {
        return this.priorityIndex;
    }

    //#region IFeature

    @Override
    public String getKey() {
        return this.feature.getKey();
    }

    @Nullable
    @Override
    public IFeatureHolder getHolder() {
        return this.feature.getHolder();
    }

    @Override
    public void setHolder(@Nullable final IFeatureHolder holder) {
        this.feature.setHolder(holder);
    }

    @Override
    public NBTTagCompound serializeNBT() {
        final NBTTagCompound tag = this.feature.serializeNBT();
        this.writeToNBT(tag);
        return tag;
    }

    @Override
    public void deserializeNBT(final NBTTagCompound nbt) {
        this.feature.deserializeNBT(nbt);
        this.readFromNBT(nbt);
        this.dirty = false;
    }

    //#endregion

    //#region IClientFeature

    @Override
    public void handleUpdateTag(final NBTTagCompound tag) {
        if (this.clientFeature != null) {
            this.clientFeature.handleUpdateTag(tag);
        }

        this.readFromNBT(tag);
    }

    //#endregion

    //#region IServerFeature

    @Override
    public boolean isDirty() {
        return this.dirty || ((this.serverFeature != null) && this.serverFeature.isDirty());
    }

    @Nullable
    @Override
    public NBTTagCompound getGuiUpdateTag(final boolean resetDirtyFlag) {
        return (this.serverFeature != null) ? this.serverFeature.getGuiUpdateTag(resetDirtyFlag) : null;
    }

    @Nullable
    @Override
    public NBTTagCompound getTickUpdateTag(final boolean resetDirtyFlag) {
        NBTTagCompound nbt = (this.serverFeature != null) ? this.serverFeature.getTickUpdateTag(resetDirtyFlag) : null;
        if (this.dirty) {
            if (nbt == null) {
                nbt = new NBTTagCompound();
            }

            this.writeToNBT(nbt);

            if (resetDirtyFlag) {
                this.dirty = false;
            }
        }

        return nbt;
    }

    @Nullable
    @Override
    public NBTTagCompound getLoadUpdateTag() {
        NBTTagCompound tag = (this.serverFeature != null) ? this.serverFeature.getLoadUpdateTag() : null;
        if (tag == null) {
            tag = new NBTTagCompound();
        }

        this.writeToNBT(tag);
        this.dirty = false; // facings were sent to client

        return tag;
    }

    //#endregion

    //#region ISidedFeature

    @Override
    public EnumFacing[] getFacings() {
        return (this.facings == null) ? new EnumFacing[0] : this.facings;
    }

    @Override
    public void setFacings(final EnumFacing[] facings) {
        this.facings = facings;
        this.dirty = true;
    }

    //#endregion

    //#region ICapabilityProvider

    @Override
    public boolean hasCapability(@Nonnull final Capability<?> capability, @Nullable final EnumFacing facing) {
        return (this.capProvider != null)
            && ((facing == null) || this.isFacingEnabled(facing))
            && this.capProvider.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull final Capability<T> capability, @Nullable final EnumFacing facing) {
        if ((this.capProvider != null) && ((facing == null) || this.isFacingEnabled(facing))) {
            return this.capProvider.getCapability(capability, facing);
        }
        return null;
    }

    @Override
    public void initCapabilities(final ICapabilitiesContainer container) {
        final ICapabilitiesContainer fake = new ICapabilitiesContainer() {
            @Override
            public <T> void addCapability(final Capability<T> capability, final Function<EnumFacing, T> capabilitySupplier, @Nullable final Predicate<EnumFacing> facingFilter) {
                container.addCapability(capability, capabilitySupplier, facing ->
                    ((facingFilter == null) || facingFilter.test(facing)) && SidedFeatureWrapper.this.isFacingEnabled(facing)
                );
            }
        };
        this.feature.initCapabilities(fake);
    }

    //#endregion

    protected void writeToNBT(final NBTTagCompound tag) {
        if ((this.facings != null) && (this.facings.length > 0)) {
            serializeNBT().setTag("facings", new NBTTagIntArray(
                Arrays
                    .stream(this.getFacings())
                    .map(EnumFacing::getIndex)
                    .collect(Collectors.toList())
            ));
        }
    }

    protected void readFromNBT(final NBTTagCompound nbt) {
        if (nbt.hasKey("facings", Constants.NBT.TAG_INT_ARRAY)) {
            this.facings = Arrays
                .stream(nbt.getIntArray("facings"))
                .mapToObj(EnumFacing::getFront)
                .toArray(EnumFacing[]::new);
        }
        else {
            this.facings = new EnumFacing[0];
        }
    }
}
