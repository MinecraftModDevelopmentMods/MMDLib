package com.mcmoddev.lib.crafting.ingredient;

public interface IFluidIngredientMatcher {
    boolean matchesFluid(IFluidIngredient ingredient, IngredientAmountMatch amountMatch);
}
