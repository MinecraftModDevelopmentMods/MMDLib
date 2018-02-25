package com.mcmoddev.lib.energy;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;

public abstract class BaseGenericEnergyStorage<T> implements IGenericEnergyStorage<T>, INBTSerializable<NBTTagCompound>, ICapabilityProvider {
    private IEnergyValue<T> stored;
    private final IEnergyValue<T> capacity;
    private final List<IEnergySystem> acceptedSystems;
    private IEnergyValue<T> inputRate;
    private IEnergyValue<T> outputRate;

    protected BaseGenericEnergyStorage(final IEnergyValue<T> initial, final IEnergyValue<T> capacity, final IEnergySystem... acceptedSystems) {
        this.stored = initial;
        this.capacity = capacity;
        this.acceptedSystems = Arrays.asList(acceptedSystems);
    }

    @Override
    public IEnergySystem<T> getBaseSystem() {
        return this.capacity.getSystem();
    }

    @Override
    public IEnergyValue<T> getCapacityValue() {
        return this.capacity.copy();
    }

    @Override
    public IEnergyValue<T> getStoredValue() {
        return this.stored.copy();
    }

    @Override
    public T getCapacity() {
        return this.capacity.getValue();
    }

    @Override
    public T getStored() {
        return this.stored.getValue();
    }

    @Override
    public IEnergyValue<T> getInputRateValue() {
        return this.inputRate.copy();
    }

    @Override
    public IEnergyValue<T> getOutputRateValue() {
        return this.outputRate.copy();
    }

    protected void setInputRateValue(final IEnergyValue<T> value) {
        this.inputRate = value;
        this.onChanged();
    }

    protected void setOutputRateValue(final IEnergyValue<T> value) {
        this.outputRate = value;
        this.onChanged();
    }

    @Override
    public T getInputRate() {
        return this.inputRate.getValue();
    }

    @Override
    public T getOutputRate() {
        return this.outputRate.getValue();
    }

    @Override
    public boolean canStore() {
        return this.getZeroEnergy().compareTo(this.inputRate) < 0;
    }

    @Override
    public boolean canTake() {
        return this.getZeroEnergy().compareTo(this.outputRate) < 0;
    }

    @Override
    public final NBTTagCompound serializeNBT() {
        final NBTTagCompound nbt = new NBTTagCompound();
        this.writeValueToNBT(nbt, "energy", this.getStored());
        this.writeValueToNBT(nbt, "input_rate", this.getInputRate());
        this.writeValueToNBT(nbt, "output_rate", this.getOutputRate());
        return nbt;
    }

    protected abstract void writeValueToNBT(NBTTagCompound nbt, String key, T energy);

    @Override
    public final void deserializeNBT(final NBTTagCompound nbt) {
        if ((nbt == null) || !nbt.hasKey("energy")) {
            this.stored = this.getZeroEnergy();
        } else {
            this.stored = this.readValueFromNBT(nbt, "energy");
        }

        if ((nbt == null) || !nbt.hasKey("input_rate")) {
            this.inputRate = this.getZeroEnergy();
        } else {
            this.inputRate = this.readValueFromNBT(nbt, "input_rate");
        }

        if ((nbt == null) || !nbt.hasKey("output_rate")) {
            this.outputRate = this.getZeroEnergy();
        } else {
            this.outputRate = this.readValueFromNBT(nbt, "output_rate");
        }
    }

    protected abstract IEnergyValue<T> getZeroEnergy();
    protected abstract IEnergyValue<T> readValueFromNBT(NBTTagCompound nbt, String key);

    @Override
    public IEnergyValue<T> storeValue(final IEnergyValue toStore, final boolean doStore) {
        final IEnergyValue<T> normalized = this.capacity.getSystem().convertFrom(toStore);
        if (normalized == null) {
            return this.getZeroEnergy();
        }

        final IEnergyValue<T> available = this.capacity.substract(this.stored);
        final IEnergyValue<T> canStore = (available.compareTo(normalized) < 0)
            ? available.copy()
            : normalized.copy();

        // TODO: account for input rate

        if (doStore && (canStore.compareTo(this.getZeroEnergy()) > 0)) {
            this.stored = this.stored.add(canStore);
            this.onChanged();
        }

        return canStore;
    }

    @Override
    public IEnergyValue<T> takeValue(final IEnergyValue toTake, final boolean doTake) {
        final IEnergyValue<T> normalized = this.capacity.getSystem().convertFrom(toTake);
        if (normalized == null) {
            return this.getZeroEnergy();
        }

        final IEnergyValue<T> canTake = (this.stored.compareTo(normalized) < 0)
            ? this.stored.copy()
            : normalized.copy();

        // TODO: account for output rate

        if (doTake && (canTake.compareTo(this.getZeroEnergy()) > 0)) {
            this.stored = this.stored.substract(canTake);
            this.onChanged();
        }

        return canTake;
    }

    protected void onChanged() {}

    @Override
    public boolean hasCapability(@Nonnull final Capability<?> capability, @Nullable final EnumFacing facing) {
        return this.acceptedSystems.stream().anyMatch(
            s -> ((IEnergyCapabilityProvider)s).hasCapability(capability, this)
        );
    }

    @Nullable
    @Override
    public <C> C getCapability(@Nonnull final Capability<C> capability, @Nullable final EnumFacing facing) {
        return this.acceptedSystems.stream().map(
            s -> ((IEnergyCapabilityProvider)s).getCapability(capability, this)
        )
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }
}
