package com.mcmoddev.lib.feature;

import java.util.Arrays;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

    private IFeature feature;
    private IClientFeature clientFeature;
    private IServerFeature serverFeature;
    private ICapabilityProvider capProvider;

    public SidedFeatureWrapper(IFeature feature) {
        this.feature = feature;
        this.clientFeature = (feature instanceof IClientFeature) ? (IClientFeature)feature : null;
        this.serverFeature = (feature instanceof IServerFeature) ? (IServerFeature)feature : null;
        this.capProvider = (feature instanceof ICapabilityProvider) ? (ICapabilityProvider)feature : null;
    }

    public static SidedFeatureWrapper wrap(IFeature feature) {
        return new SidedFeatureWrapper(feature);
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
    public void setHolder(@Nullable IFeatureHolder holder) {
        this.feature.setHolder(holder);
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = this.feature.serializeNBT();
        this.writeToNBT(tag);
        return tag;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        this.feature.deserializeNBT(nbt);
        this.readFromNBT(nbt);
        this.dirty = false;
    }

    //#endregion

    //#region IClientFeature

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
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
    public NBTTagCompound getGuiUpdateTag(boolean resetDirtyFlag) {
        return (this.serverFeature != null) ? this.serverFeature.getGuiUpdateTag(resetDirtyFlag) : null;
    }

    @Nullable
    @Override
    public NBTTagCompound getTickUpdateTag(boolean resetDirtyFlag) {
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
    public void setFacings(EnumFacing[] facings) {
        this.facings = facings;
        this.dirty = true;
    }

    //#endregion

    //#region ICapabilityProvider

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        return (this.capProvider != null)
            && ((facing == null) || this.isFacingEnabled(facing))
            && this.capProvider.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        if ((this.capProvider != null) && ((facing == null) || this.isFacingEnabled(facing))) {
            return this.capProvider.getCapability(capability, facing);
        }
        return null;
    }

    //#endregion

    protected void writeToNBT(NBTTagCompound tag) {
        if ((this.facings != null) && (this.facings.length > 0)) {
            serializeNBT().setTag("facings", new NBTTagIntArray(
                Arrays
                    .stream(this.getFacings())
                    .map(EnumFacing::getIndex)
                    .collect(Collectors.toList())
            ));
        }
    }

    protected void readFromNBT(NBTTagCompound nbt) {
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
