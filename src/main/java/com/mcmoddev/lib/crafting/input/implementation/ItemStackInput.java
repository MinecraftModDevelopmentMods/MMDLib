package com.mcmoddev.lib.crafting.input.implementation;

import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

public class ItemStackInput extends BaseItemInput {
    private final ItemStack stack;

    public ItemStackInput(String key, ItemStack stack) {
        super(key, stack.getCount());
        this.stack = stack;
    }

    @Override
    void populatePossibleInputs(NonNullList<ItemStack> stacks) {
        if (!this.stack.isEmpty()) {
            stacks.add(this.stack);
        }
    }
}
