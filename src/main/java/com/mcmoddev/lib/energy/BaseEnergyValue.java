package com.mcmoddev.lib.energy;

/**
 * Base implementation of {@link IEnergyValue}.
 * @param <T>
 */
public abstract class BaseEnergyValue<T> implements IEnergyValue<T> {
    private final IEnergySystem<T> system;
    private T value;

    /**
     * Initializes a new instance of BaseEnergyValue.
     * @param system The energy system corresponding to this energy value.
     * @param value The actual value of the energy.
     */
    protected BaseEnergyValue(final IEnergySystem<T> system, final T value) {
        this.system = system;
        this.value = value;
    }

    @Override
    public IEnergySystem<T> getSystem() {
        return this.system;
    }

    @Override
    public T getValue() {
        return this.value;
    }

    @Override
    public void setValue(final T value) {
        this.value = value;
    }
}
