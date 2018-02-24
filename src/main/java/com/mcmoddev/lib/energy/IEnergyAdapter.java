package com.mcmoddev.lib.energy;

public interface IEnergyAdapter {
    IEnergySystem getSystem();

    IEnergyValue getCapacity();
    IEnergyValue getValue();

    IEnergyValue charge(IEnergyValue value, boolean doCharge);
    IEnergyValue discharge(IEnergyValue value, boolean doDischarge);
}
