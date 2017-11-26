package com.mcmoddev.lib.crafting.input;

import javax.annotation.Nullable;
import net.minecraft.item.ItemStack;

public interface ICraftingItemInput extends ITypedCraftingInput<ItemStack> {
    @Override
    default Class<ItemStack> getInputClass() {
        return ItemStack.class;
    }

    @Nullable
    @Override
    default ItemStack getNullInput() {
        return ItemStack.EMPTY;
    }
}
