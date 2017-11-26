package com.mcmoddev.lib.crafting.ingredient;

import javax.annotation.Nullable;
import net.minecraftforge.fluids.FluidStack;

public interface IFluidIngredient extends ICraftingIngredient {
    @Nullable
    FluidStack getFluidStack();

    @Override
    default int getAmount() {
        FluidStack fluid = this.getFluidStack();
        return (fluid == null) ? 0 : fluid.amount;
    }
}
