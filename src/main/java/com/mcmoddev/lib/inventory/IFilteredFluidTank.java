package com.mcmoddev.lib.inventory;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;

/**
 * Represents a Fluid Tank that supports filtering of what goes in/out of it.
 */
public interface IFilteredFluidTank extends IFluidTank {
    /**
     * Tests if the specified fluid can be inserted into this tank
     * @param fluid The fluid to be inserted.
     * @return True if the fluid can be inserted. False otherwise.
     */
    boolean canFill(FluidStack fluid);

    /**
     * Tests if the specified fluid can be extracted from this tank
     * @param fluid The fluid to be extracted.
     * @return True if the fluid can be extracted. False otherwise.
     */
    boolean canDrain(FluidStack fluid);
}
