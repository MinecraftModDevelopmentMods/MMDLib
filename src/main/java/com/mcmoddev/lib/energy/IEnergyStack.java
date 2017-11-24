package com.mcmoddev.lib.energy;

public interface IEnergyStack<N extends Comparable<N>> {
    N getValue();
    void setValue(N value);
}
