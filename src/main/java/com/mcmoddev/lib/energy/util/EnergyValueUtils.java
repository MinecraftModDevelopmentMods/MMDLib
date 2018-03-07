package com.mcmoddev.lib.energy.util;

import com.mcmoddev.lib.energy.EnergySystemRegistry;
import com.mcmoddev.lib.energy.ForgeEnergyValue;
import com.mcmoddev.lib.energy.IEnergyValue;

public final class EnergyValueUtils {
    protected EnergyValueUtils() {}

    public static boolean canGetForgeEnergy(final IEnergyValue value) {
        return (value instanceof ForgeEnergyValue) || value.getSystem().isCompatibleWith(EnergySystemRegistry.FORGE_ENERGY);
    }

    public static int getForgeEnergy(final IEnergyValue value) {
        if (value instanceof ForgeEnergyValue) {
            return ((ForgeEnergyValue)value).getValue();
        }
        else if (EnergySystemRegistry.FORGE_ENERGY.isCompatibleWith(value.getSystem())) {
            final ForgeEnergyValue energy = EnergySystemRegistry.FORGE_ENERGY.convertFrom(value);
            return (energy == null) ? 0 : energy.getValue();
        }

        // TODO: consider throwing an error or something
        return 0;
    }
}
