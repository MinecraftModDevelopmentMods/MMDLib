package com.mcmoddev.lib.crafting.ingredient;

public interface IEnergyIngredientMatcher {
    boolean matchesEnergy(IEnergyIngredient ingredient, IngredientAmountMatch amountMatch);
}
