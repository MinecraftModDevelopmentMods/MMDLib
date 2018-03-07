package com.mcmoddev.lib.crafting.input;

import com.mcmoddev.lib.crafting.base.ICraftingObject;

/**
 * Represents an input into a recipe.
 */
public interface ICraftingInput extends ICraftingObject {
    int getAmount();
}
