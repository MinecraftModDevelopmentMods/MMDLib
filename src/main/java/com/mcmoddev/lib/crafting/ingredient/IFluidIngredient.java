package com.mcmoddev.lib.crafting.ingredient;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

public interface IFluidIngredient extends ICraftingIngredient, IItemIngredientMatcher {
    FluidStack getFluidStack();

    @Override
    default boolean isMatch(ICraftingIngredient ingredient, IngredientAmountMatch amountMatch) {
        if (ingredient instanceof IFluidIngredient) {
            if (!amountMatch.compare(this, ingredient)) {
                // amounts don't match... don't even care about fluids.
                return false;
            }
            FluidStack fluid = ((IFluidIngredient)ingredient).getFluidStack();
            return this.getFluidStack().isFluidEqual(fluid);
        }
        else if (ingredient instanceof IFluidIngredientMatcher) {
            return ((IFluidIngredientMatcher)ingredient).matchesFluid(this, amountMatch);
        }
        return false;
    }

    @Override
    default boolean matchesItem(IItemIngredient ingredient, IngredientAmountMatch amountMatch) {
        FluidStack fluid = this.getFluidStack();
        for(ItemStack stack : ingredient.getItemStacks()) {
            FluidStack other = FluidUtil.getFluidContained(stack);
            if ((other != null) && other.isFluidEqual(fluid) && amountMatch.compare(other.amount, fluid.amount))
                return true;
        }
        return false;
    }
}
