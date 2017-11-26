package com.mcmoddev.lib.crafting.extractor;

import javax.annotation.Nullable;
import com.mcmoddev.lib.crafting.base.ICraftingObject;
import com.mcmoddev.lib.crafting.ingredient.ICraftingIngredient;
import com.mcmoddev.lib.crafting.input.ICraftingInput;
import com.mcmoddev.lib.crafting.inventory.ICraftingInventory;

public interface ICraftingInputExtractor extends ICraftingObject {
    @Nullable
    ICraftingIngredient extract(ICraftingInput input, ICraftingInventory inventory, boolean simulate);
}
