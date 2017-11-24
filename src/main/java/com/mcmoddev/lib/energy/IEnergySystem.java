package com.mcmoddev.lib.energy;

public interface IEnergySystem<T extends IEnergyStack> {
    String getName();
    Boolean canConvertTo(IEnergySystem system);
    <R extends IEnergyStack> R convertTo(IEnergySystem<R> system);
}
