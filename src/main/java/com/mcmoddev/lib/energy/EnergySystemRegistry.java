package com.mcmoddev.lib.energy;

import java.util.Arrays;
import com.mcmoddev.lib.energy.rf.RFEnergySystem;
import com.mcmoddev.lib.energy.tesla.TeslaEnergySystem;

public final class EnergySystemRegistry {
    public static final ForgeEnergySystem FORGE_ENERGY = new ForgeEnergySystem();
    public static final TeslaEnergySystem TESLA = new TeslaEnergySystem();
    public static final RFEnergySystem RF = new RFEnergySystem();

    public static final IEnergySystem[] ALL = new IEnergySystem[] { FORGE_ENERGY, TESLA, RF };

    public static IEnergySystem[] getAllCompatible(final IEnergySystem baseSystem) {
        return Arrays.stream(ALL)
            .filter(IEnergySystem::isAvailable)
            .filter(baseSystem::isCompatibleWith)
            .toArray(IEnergySystem[]::new);
    }
}
