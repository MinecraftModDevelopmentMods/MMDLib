package com.mcmoddev.lib.crafting.ingredient;

import net.minecraft.item.ItemStack;

public interface IItemIngredient extends ICraftingIngredient {
    ItemStack getItemStack();

    @Override
    default int getAmount() {
        return this.getItemStack().getCount();
    }
}
