package com.mcmoddev.lib.crafting.ingredient.implementation;

import com.mcmoddev.lib.crafting.ingredient.IItemIngredient;
import net.minecraft.item.ItemStack;

public class ItemIngredient extends BaseCraftingIngredient implements IItemIngredient {
    private final ItemStack wrapped;

    public ItemIngredient(ItemStack wrapped) {
        super();

        this.wrapped = wrapped;
    }

    @Override
    public ItemStack getItemStack() {
        return this.wrapped;
    }
}
