package com.mcmoddev.lib.energy;

/**
 * Provides a base interface for energy storage.
 * @param <T> The type of the value this energy storage's {@link #getBaseSystem() base energy system} works with.
 *           (for example: {@link Integer} for RF and ForgeEnergy, {@link Long} for Tesla)
 */
public interface IGenericEnergyStorage<T> {
    /**
     * Gets the base {@link IEnergySystem} that is used
     * @return
     */
    IEnergySystem<T> getBaseSystem();

    /**
     * Gets the capacity of this storage as an {@link IEnergyValue}.
     * @return The capacity of this storage as an {@link IEnergyValue} of the {@link #getBaseSystem()}.
     */
    IEnergyValue<T> getCapacityValue();

    /**
     * Gets the energy stored in this storage as an {@link IEnergyValue}.
     * @return The energy stored in this storage as an {@link IEnergyValue} of the {@link #getBaseSystem()}.
     */
    IEnergyValue<T> getStoredValue();

    /**
     * Gets the actual value of the capacity of this storage.
     * @return The actual value of the capacity of this storage.
     */
    T getCapacity();

    /**
     * Gets the actual value of the energy stored in this storage.
     * @return The actual value of the energy stored in this storage.
     */
    T getStored();

    /**
     * Tries to store the specified amount of energy into this storage.
     * @param toStore The amount of energy to store.
     * @param doStore True if the energy should actually be stored. False if this is just a simulation.
     * @return The amount of energy that was, or would have been, actually stored in this storage as an {@link IEnergyValue energy value} of the {@link #getBaseSystem() base energy system}.
     */
    IEnergyValue<T> storeValue(IEnergyValue toStore, boolean doStore);

    /**
     * Tries to take the specified amount of energy from this storage.
     * @param toTake The amount of energy to be taken.
     * @param doTake True if the energy should actually be taken. False if this is just a simulation.
     * @return The amount of energy that was, or would have been, actually taken from this storage as an {@link IEnergyValue energy value} of the {@link #getBaseSystem() base energy system}.
     */
    IEnergyValue<T> takeValue(IEnergyValue toTake, boolean doTake);

    /**
     * Tries to store the specified amount of energy into this storage.
     * @param toStore The amount of energy to store.
     * @param doStore True if the energy should actually be stored. False if this is just a simulation.
     * @return The amount of energy that was, or would have been, actually stored in this storage.
     */
    T store(T toStore, boolean doStore);

    /**
     * Tries to take the specified amount of energy from this storage.
     * @param toTake The amount of energy to be taken.
     * @param doTake True if the energy should actually be taken. False if this is just a simulation.
     * @return The amount of energy that was, or would have been, actually taken from this storage.
     */
    T take(T toTake, boolean doTake);

    /**
     * Returns the maximum input rate of this storage.
     * @return The maximum input rate of this storage as an {@link IEnergyValue energy value} of the {@link #getBaseSystem() base energy system}.
     */
    IEnergyValue<T> getInputRateValue();

    /**
     * Returns the maximum output rate of this storage.
     * @return The maximum output rate of this storage as an {@link IEnergyValue energy value} of the {@link #getBaseSystem() base energy system}.
     */
    IEnergyValue<T> getOutputRateValue();

    /**
     * Returns the maximum input rate of this storage.
     * @return The maximum input rate of this storage.
     */
    T getInputRate();

    /**
     * Returns the maximum output rate of this storage.
     * @return The maximum output rate of this storage.
     */
    T getOutputRate();

    /**
     * Returns a value indicating if this storage can be used to store energy.
     * @return True if this storage can store energy. False otherwise.
     * @implNote This is usually an indication of the {@link #getInputRate() input rate} being greater than 0.
     */
    boolean canStore();

    /**
     * Returns a value indicating if this storage can be used to provide energy.
     * @return True if this storage can provide energy. False otherwise.
     * @implNote This is usually an indication of the {@link #getOutputRate() output rate} being greater than 0.
     */
    boolean canTake();
}
