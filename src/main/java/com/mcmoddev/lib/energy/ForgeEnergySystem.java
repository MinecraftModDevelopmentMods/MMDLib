package com.mcmoddev.lib.energy;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

@SuppressWarnings("rawtypes")
public class ForgeEnergySystem implements IForgeEnergyCompatible<Integer> {
    @Override
    public String getDependencyModId() {
        return "minecraft";
    }

    @Override
    @Nullable
    public ForgeEnergyAdapter createAdapter(final TileEntity tile, final EnumFacing facing) {
        final IEnergyStorage storage = tile.getCapability(CapabilityEnergy.ENERGY, facing);
        return (storage != null) ? new ForgeEnergyAdapter(storage) : null;
    }

    @Override
    @Nullable
    public ForgeEnergyAdapter createAdapter(final ItemStack stack) {
        final IEnergyStorage storage = stack.getCapability(CapabilityEnergy.ENERGY, null);
        return (storage != null) ? new ForgeEnergyAdapter(storage) : null;
    }

    @Override
    public boolean isCompatibleWith(final IEnergySystem system) {
        return (system == EnergySystemRegistry.FORGE_ENERGY) || (system instanceof IForgeEnergyCompatible);
    }

    @Override
    @Nullable
    public ForgeEnergyValue convertFrom(final IEnergyValue value) {
        if (value instanceof ForgeEnergyValue) {
            return ForgeEnergyValue.class.cast(value);
        }
        else if (value.getSystem() instanceof IForgeEnergyCompatible) {
            //noinspection unchecked
            return ((IForgeEnergyCompatible) value.getSystem()).convertToFE(value);
        }

        return null;
    }

    @Nullable
    @Override
    public ForgeEnergyValue convertToFE(final IEnergyValue value) {
        return (value instanceof ForgeEnergyValue) ? (ForgeEnergyValue)value : null;
    }

    @Nullable
    @Override
    public IEnergyValue<Integer> convertFromFE(final ForgeEnergyValue value) {
        return value;
    }

    @Override
    public boolean hasCapability(final Capability<?> capability, final IGenericEnergyStorage storage) {
        return (capability == CapabilityEnergy.ENERGY) && (storage.getBaseSystem() instanceof IForgeEnergyCompatible);
    }

    @Nullable
    @Override
    public <C> C getCapability(final Capability<C> capability, final IGenericEnergyStorage storage) {
        if (this.hasCapability(capability, storage)) {
            return CapabilityEnergy.ENERGY.cast(new CapabilityStorage(storage));
        }
        return null;
    }

    private static class CapabilityStorage implements IEnergyStorage {
        private final IGenericEnergyStorage storage;
        private final IForgeEnergyCompatible compatible;

        CapabilityStorage(final IGenericEnergyStorage storage) {
            this.storage = storage;
            // no worries about cast error, it's supposed to crash and burn if wrong storage gets passed in
            this.compatible = (IForgeEnergyCompatible)storage.getBaseSystem();
        }

        @Override
        public int receiveEnergy(final int maxReceive, final boolean simulate) {
            return Objects.requireNonNull(this.compatible.convertToFE(this.storage.storeValue(new ForgeEnergyValue(maxReceive), !simulate)))
                .getValue();
        }

        @Override
        public int extractEnergy(final int maxExtract, final boolean simulate) {
            return Objects.requireNonNull(this.compatible.convertToFE(this.storage.takeValue(new ForgeEnergyValue(maxExtract), !simulate)))
                .getValue();
        }

        @Override
        public int getEnergyStored() {
            return EnergySystemRegistry.FORGE_ENERGY.convertToFE(this.storage.getStoredValue()).getValue();
        }

        @Override
        public int getMaxEnergyStored() {
            return EnergySystemRegistry.FORGE_ENERGY.convertToFE(this.storage.getCapacityValue()).getValue();
        }

        @Override
        public boolean canExtract() {
            return this.storage.canTake();
        }

        @Override
        public boolean canReceive() {
            return this.storage.canStore();
        }
    }
}
