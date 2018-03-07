package com.mcmoddev.lib.crafting.ingredient.implementation;

import javax.annotation.Nullable;
import com.mcmoddev.lib.crafting.ingredient.IFluidIngredient;
import net.minecraftforge.fluids.FluidStack;

public class FluidIngredient extends BaseCraftingIngredient implements IFluidIngredient {
    private final FluidStack wrapped;

    public FluidIngredient(FluidStack wrapped) {
        super();

        this.wrapped = wrapped;
    }

    @Nullable
    @Override
    public FluidStack getFluidStack() {
        return this.wrapped;
    }
}
