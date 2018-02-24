package com.mcmoddev.lib.energy;

public abstract class BaseEnergyValue<T> implements IEnergyValue<T> {
    private final IEnergySystem<T> system;
    private T value;

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
