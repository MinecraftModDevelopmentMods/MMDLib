package com.mcmoddev.lib.util;

import net.minecraftforge.fluids.FluidStack;

public final class FluidStackUtils {
    private FluidStackUtils() { }

    public static FluidStack copyWithSize(FluidStack fluid, int amount) {
        if (amount > 0) {
            FluidStack result = fluid.copy();
            result.amount = amount;
            return result;
        }
        else {
            return null;
        }
    }
}
