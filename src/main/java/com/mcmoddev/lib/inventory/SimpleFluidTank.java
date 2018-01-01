package com.mcmoddev.lib.inventory;

import javax.annotation.Nullable;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

public class SimpleFluidTank extends FluidTank implements IFluidTankModifiable {
    public SimpleFluidTank(int capacity) {
        super(capacity);
    }

    public SimpleFluidTank(@Nullable FluidStack fluidStack, int capacity) {
        super(fluidStack, capacity);
    }

    public SimpleFluidTank(Fluid fluid, int amount, int capacity) {
        super(fluid, amount, capacity);
    }
}
