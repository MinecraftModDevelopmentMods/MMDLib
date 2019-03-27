package com.mcmoddev.lib.energy;

import javax.annotation.Nullable;
import net.minecraftforge.common.capabilities.Capability;

/**
 * Provides a way for an {@link IEnergySystem} to provide capabilities for an {@link IGenericEnergyStorage} instance.
 */
@SuppressWarnings("rawtypes")
public interface IEnergyCapabilityProvider {
    /**
     * Determines if this object has support for the capability in question for a specific energy storage.
     * The return value of this MIGHT change during runtime if this object gains or looses support
     * for a capability.
     *
     * This is a light weight version of getCapability, intended for metadata uses.
     *
     * @param capability The capability to check
     * @param storage The energy storage the resulting capability should be attached to.
     * @return True if this object supports the capability.
     */
    boolean hasCapability(Capability<?> capability, IGenericEnergyStorage storage);

    /**
     * Retrieves the handler for the capability requested on the specific energy storage.
     * The return value CAN be null if the object does not support the capability.
     *
     * @param capability The capability to check
     * @param storage The energy storage the resulting capability should be attached to.
     * @return The requested capability. Returns null when {@link #hasCapability(Capability, IGenericEnergyStorage)} would return false.
     */
    @Nullable
    <C> C getCapability(Capability<C> capability, IGenericEnergyStorage storage);
}
