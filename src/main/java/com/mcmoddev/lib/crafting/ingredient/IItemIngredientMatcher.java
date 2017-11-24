package com.mcmoddev.lib.crafting.ingredient;

public interface IItemIngredientMatcher {
    boolean matchesItem(IItemIngredient ingredient, IngredientAmountMatch amountMatch);
}
