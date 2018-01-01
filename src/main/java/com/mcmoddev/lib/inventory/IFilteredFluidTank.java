package com.mcmoddev.lib.inventory;

import net.minecraftforge.fluids.FluidStack;

public interface IFilteredFluidTank {
    boolean canFill(FluidStack fluid);
    boolean canDrain(FluidStack fluid);
}
