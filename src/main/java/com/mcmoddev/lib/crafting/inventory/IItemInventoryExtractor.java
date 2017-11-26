package com.mcmoddev.lib.crafting.inventory;

import javax.annotation.Nullable;
import com.mcmoddev.lib.crafting.ingredient.ICraftingIngredient;

public interface IItemInventoryExtractor {
    @Nullable
    ICraftingIngredient extractItemFrom(IFluidInventory inventory, int slot, boolean simulate);
}
