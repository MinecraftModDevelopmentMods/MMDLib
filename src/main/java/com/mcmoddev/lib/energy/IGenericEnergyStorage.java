package com.mcmoddev.lib.energy;

public interface IGenericEnergyStorage<T> {
    IEnergySystem<T> getBaseSystem();

    IEnergyValue<T> getCapacityValue();
    IEnergyValue<T> getStoredValue();

    T getCapacity();
    T getStored();

    IEnergyValue<T> storeValue(IEnergyValue toStore, boolean doStore);
    IEnergyValue<T> takeValue(IEnergyValue toTake, boolean doTake);

    T store(T toStore, boolean doStore);
    T take(T toTake, boolean doTake);

    IEnergyValue<T> getInputRateValue();
    IEnergyValue<T> getOutputRateValue();
    T getInputRate();
    T getOutputRate();

    boolean canStore();
    boolean canTake();
}
