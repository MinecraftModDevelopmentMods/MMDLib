package com.mcmoddev.lib.inventory;

import javax.annotation.Nullable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * The equivalent if {@link IItemHandlerModifiable} for fluid tanks.
 */
public interface IFluidTankModifiable extends IFluidTank {
    /**
     * Overrides the fluid in this tank.
     *
     * @param fluid FluidStack to set tank content to (may be empty or null).
     * @throws RuntimeException if the handler is called in a way that the handler was not expecting.
     **/
    void setFluid(@Nullable FluidStack fluid);
}
