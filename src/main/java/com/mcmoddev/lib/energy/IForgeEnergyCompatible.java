package com.mcmoddev.lib.energy;

import javax.annotation.Nullable;

/**
 * Provides an easy "automatic" way of converting between two energy system by using Forge Energy as a middle ground.
 * @param <T> The type of the value this energy system works with. See {@link IEnergySystem} for details.
 */
public interface IForgeEnergyCompatible<T> extends IEnergySystem<T> {
    /**
     * Converts an energy value of this energy system into Forge Energy.
     * @param value The value representing an energy value of this system.
     * @return The corresponding Forge Energy value. Or null if conversion is not possible.
     */
    @Nullable
    ForgeEnergyValue convertToFE(IEnergyValue value);

    /**
     * Converts a Forge Energy value into an energy value of this system.
     * @param value The Forge Energy value to be converted.
     * @return The converted energy value. Or null if conversion was not possible.
     */
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
