package com.mcmoddev.lib.crafting.inventory;

import com.mcmoddev.lib.crafting.ingredient.ICraftingIngredient;

public interface ICraftingInventory {
    String getKey();

    int getSlots();
    ICraftingIngredient getIngredient(int slot);
}
