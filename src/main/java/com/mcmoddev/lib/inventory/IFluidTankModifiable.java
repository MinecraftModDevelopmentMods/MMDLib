package com.mcmoddev.lib.inventory;

import javax.annotation.Nullable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;

public interface IFluidTankModifiable extends IFluidTank {
    void setFluid(@Nullable FluidStack fluid);
}
