package com.mcmoddev.lib.crafting.ingredient;

import com.mcmoddev.lib.crafting.inventory.ICraftingInventory;

public interface ICraftingIngredient {
    boolean isMatch(ICraftingIngredient ingredient, IngredientAmountMatch amountMatch);

    default boolean isSame(ICraftingIngredient ingredient) {
        return this.isMatch(ingredient, IngredientAmountMatch.EXACT);
    }

    default boolean isEnough(ICraftingIngredient ingredient) {
        return this.isMatch(ingredient, IngredientAmountMatch.BE_ENOUGH);
    }

    default boolean isSameIngredient(ICraftingIngredient ingredient) {
        return this.isMatch(ingredient, IngredientAmountMatch.EXACT);
    }

    int getAmount();

    ICraftingIngredient extractFrom(ICraftingInventory inventory, int slot, boolean simulate);
}
