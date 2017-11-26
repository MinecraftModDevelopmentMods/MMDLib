package com.mcmoddev.lib.crafting.input;

import javax.annotation.Nullable;
import net.minecraftforge.fluids.FluidStack;

public interface ICraftingFluidInput extends ITypedCraftingInput<FluidStack> {
    @Override
    default Class<FluidStack> getInputClass() {
        return FluidStack.class;
    }

    @Nullable
    @Override
    default FluidStack getNullInput() {
        return null;
    }
}
