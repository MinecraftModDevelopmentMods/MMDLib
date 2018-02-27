package com.mcmoddev.lib.energy;

import net.minecraft.nbt.NBTTagCompound;

public class ForgeEnergyStorage extends BaseGenericEnergyStorage<Integer> {
    public ForgeEnergyStorage(final int capacity) {
        this(0, capacity);
    }

    public ForgeEnergyStorage(final int initial, final int capacity) {
        super(
            new ForgeEnergyValue(initial),
            new ForgeEnergyValue(capacity),
            EnergySystemRegistry.getAllCompatible(EnergySystemRegistry.FORGE_ENERGY));
    }

    @Override
    protected void writeValueToNBT(final NBTTagCompound nbt, final String key, final Integer energy) {
        nbt.setInteger(key, energy);
    }

    @Override
    protected IEnergyValue<Integer> readValueFromNBT(final NBTTagCompound nbt, final String key) {
        return new ForgeEnergyValue(nbt.getInteger(key));
    }

    @Override
    protected IEnergyValue<Integer> getZeroEnergy() {
        return new ForgeEnergyValue(0);
    }

    @Override
    public Integer store(final Integer toStore, final boolean doStore) {
        return this.storeValue(new ForgeEnergyValue(toStore), doStore).getValue();
    }

    @Override
    public Integer take(final Integer toTake, final boolean doTake) {
        return this.takeValue(new ForgeEnergyValue(toTake), doTake).getValue();
    }

    public ForgeEnergyStorage setInputRate(final int value) {
        this.setInputRateValue(new ForgeEnergyValue(value));
        return this;
    }

    public ForgeEnergyStorage setoutputRate(final int value) {
        this.setOutputRateValue(new ForgeEnergyValue(value));
        return this;
    }
}
