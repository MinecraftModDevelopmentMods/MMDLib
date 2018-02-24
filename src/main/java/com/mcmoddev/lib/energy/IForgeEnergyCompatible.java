package com.mcmoddev.lib.energy;

import javax.annotation.Nullable;

public interface IForgeEnergyCompatible<T> extends IEnergySystem<T> {
    @Nullable
    ForgeEnergyValue convertToFE(IEnergyValue value);
    @Nullable
    IEnergyValue<T> convertFromFE(ForgeEnergyValue value);

    @Override
    default boolean isCompatibleWith(final IEnergySystem system) {
        return (system == EnergySystemRegistry.FORGE_ENERGY) || (system instanceof IForgeEnergyCompatible);
    }

    @Nullable
    @Override
    default IEnergyValue<T> convertFrom(final IEnergyValue value) {
        ForgeEnergyValue energy = null;
        if (value instanceof ForgeEnergyValue) {
            energy = ForgeEnergyValue.class.cast(value);
        }
        else if (value.getSystem() instanceof IForgeEnergyCompatible) {
            //noinspection unchecked
            energy = ((IForgeEnergyCompatible) value.getSystem()).convertToFE(value);
        }

        if (energy == null) {
            return null;
        }

        return this.convertFromFE(energy);
    }
}
