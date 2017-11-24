package com.mcmoddev.lib.energy.implementation;

import com.mcmoddev.lib.energy.IEnergyStack;

public abstract class BaseEnergyStack<N extends Comparable<N>> implements IEnergyStack<N> {
    private N value;

    BaseEnergyStack(N initial) {
        this.value = initial;
    }

    @Override
    public N getValue() {
        return this.value;
    }

    @Override
    public void setValue(N value) {
        this.value = value;
    }
}
