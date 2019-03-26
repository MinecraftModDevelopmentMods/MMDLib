package com.mcmoddev.lib.energy;

/**
 * Represents a common way to handle consumers/providers/holders of different energy from various energy system.
 * This is in most cases attached to a TileEntity or an ItemStack.
 */
@SuppressWarnings("rawtypes")
public interface IEnergyAdapter {
    /**
     * Gets the energy system this adapter was created for.
     * @return The System!
     */
    IEnergySystem getSystem();

    /**
     * Gets the max energy value the target of this adapter can hold.
     * @return The max energy value the target of this adapter can hold.
     */
    IEnergyValue getCapacity();

    /**
     * Gets the currently stored energy value from the target of this adapter.
     * @return The currently stored energy value from the target of this adapter.
     */
    IEnergyValue getValue();

    /**
     * Tries to charge the target of this adapter with the specified energy value.
     * @param value The amount of energy that should be fed into the target.
     * @param doCharge True to actually charge the target of this adapter. False to just simulate charging.
     * @return A value in this adapter's {@link IEnergySystem} specifying with how much power its target was, or woud have been, actually charged.
     */
    IEnergyValue charge(IEnergyValue value, boolean doCharge);

    /**
     * Tried to discharge the specified energy value from the target of this adapter.
     * @param value The amount of energy the should be discharged from the target.
     * @param doDischarge True to actually discharge the target of this adapter. False to just simulate discharging.
     * @return A value in this adapter's {@link IEnergySystem} specifying how much power was, or would have been, discharged from its target.
     */
    IEnergyValue discharge(IEnergyValue value, boolean doDischarge);
}
