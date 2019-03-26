package com.mcmoddev.lib.energy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * Base implementation of an {@link IGenericEnergyStorage}.
 * @param <T> The type of energy value this storage stores. (ie. Integer for Forge Energy and RF, Long for Tesla etc)
 */
@SuppressWarnings("rawtypes")
public abstract class BaseGenericEnergyStorage<T> implements IGenericEnergyStorage<T>, INBTSerializable<NBTTagCompound>, ICapabilityProvider {
    private IEnergyValue<T> stored;
    private final IEnergyValue<T> capacity;
    private final List<IEnergySystem> acceptedSystems;
    private IEnergyValue<T> inputRate;
    private IEnergyValue<T> outputRate;

    private final List<Runnable> onChangeListeners = new ArrayList<>();

    /**
     * Initializes a new instance of BaseGenericEnergyStorage
     * @param initial Initial stored energy.
     * @param capacity Total capacity of the storage.
     * @param acceptedSystems Energy systems that will be accepted (via capabilities and adapters) by this storage.
     */
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

    /**
     * Sets the input rate of this storage as an energy value.
     * @param value The input rate energy value.
     */
    protected void setInputRateValue(final IEnergyValue<T> value) {
        this.inputRate = value;
        this.onChanged();
    }

    /**
     * Sets the output rate of this storage as an energy value.
     * @param value The output rate energy value.
     */
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

    /**
     * Stores an energy value's actual value in NBT.
     * @param nbt The tag compound to store in.
     * @param key The key of the energy value.
     * @param energy The energy value.
     */
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

    /**
     * Returns an energy value of the base energy system that represents no energy.
     * @return
     */
    protected abstract IEnergyValue<T> getZeroEnergy();

    /**
     * Reads an energy value's actual value from NBT.
     * @param nbt The tag compound that stores the value.
     * @param key The key of the energy value.
     * @return The energy value.
     */
    protected abstract IEnergyValue<T> readValueFromNBT(NBTTagCompound nbt, String key);

    @Override
    public IEnergyValue<T> storeValue(final IEnergyValue toStore, final boolean doStore) {
        final IEnergyValue<T> normalized = this.capacity.getSystem().convertFrom(toStore);
        if (normalized == null) {
            return this.getZeroEnergy();
        }

        final IEnergyValue<T> available = this.capacity.subtract(this.stored);
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
            this.stored = this.stored.subtract(canTake);
            this.onChanged();
        }

        return canTake;
    }

    /**
     * Called after any property of this storage was changed.
     */
    protected void onChanged() {
        for(final Runnable listener : this.onChangeListeners) {
            listener.run();
        }
    }

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

    // TODO: this is kind of hacky... do it better!
    private void addOnChangeListener(final Runnable listener) {
        this.onChangeListeners.add(listener);
    }

    /**
     * Gets a capability provider that can be used to manipulate an energy storage stored inside an item stack.
     * @param stack The item stack that contains the energy storage.
     * @return The capability provider that can be used to manipulate the contained energy storage.
     */
    public ICapabilityProvider getProviderForItemStack(final ItemStack stack) {
        return this.getProviderForItemStack(stack, null, null);
    }

    /**
     * Gets a capability provider that can be used to manipulate an energy storage stored inside an item stack.
     * @param stack The item stack that contains the energy storage.
     * @param capabilityPreFilter Predicate used to test if the capability should actually be provided or not for the
     *                            current state of the item stack.
     * @param changedUpdate Consumer that will get called when the stored energy value is changed.
     * @return The capability provider that can be used to manipulate the contained energy storage.
     */
    public ICapabilityProvider getProviderForItemStack(final ItemStack stack,
                                                       @Nullable final BiPredicate<ItemStack, Capability> capabilityPreFilter,
                                                       @Nullable final BiConsumer<ItemStack, IEnergyStorage> changedUpdate) {
        this.addOnChangeListener(() -> {
            stack.setTagInfo("mmd_battery", this.serializeNBT());
            if (changedUpdate != null) {
                changedUpdate.accept(stack, BaseGenericEnergyStorage.this.getCapability(CapabilityEnergy.ENERGY, null));
            }
        });
        if (stack.getSubCompound("mod_battery") == null) {
            // initial setup, set nbt
            BaseGenericEnergyStorage.this.onChanged();
        }

        return new ICapabilityProvider() {
            @Override
            public boolean hasCapability(@Nonnull final Capability<?> capability, @Nullable final EnumFacing facing) {
                return ((capabilityPreFilter == null) || capabilityPreFilter.test(stack, capability))
                    && BaseGenericEnergyStorage.this.hasCapability(capability, facing);
            }

            @Nullable
            @Override
            public <C> C getCapability(@Nonnull final Capability<C> capability, @Nullable final EnumFacing facing) {
                if (!this.hasCapability(capability, facing)) {
                    return null;
                }

                BaseGenericEnergyStorage.this.deserializeNBT(stack.getSubCompound("mmd_battery"));
                return BaseGenericEnergyStorage.this.getCapability(capability, facing);
            }
        };
    }
}
