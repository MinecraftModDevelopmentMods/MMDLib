package com.mcmoddev.lib.crafting.inventory;

import javax.annotation.Nullable;
import com.mcmoddev.lib.crafting.ingredient.ICraftingIngredient;

public interface IFluidInventoryInserter {
    @Nullable
    ICraftingIngredient insertFluidInto(IFluidInventory inventory, int slot, boolean simulate);
}
