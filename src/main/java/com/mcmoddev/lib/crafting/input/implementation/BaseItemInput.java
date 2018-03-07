package com.mcmoddev.lib.crafting.input.implementation;

import java.util.List;
import com.mcmoddev.lib.crafting.input.ICraftingItemInput;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

public abstract class BaseItemInput extends BaseCraftingInput implements ICraftingItemInput {
    private NonNullList<ItemStack> stacksCache = null;

    protected BaseItemInput(String key, int amount) {
        super(key, amount);
    }

    @Override
    public final List<ItemStack> getPossibleInputs() {
        if (this.stacksCache == null) {
            this.stacksCache = NonNullList.create();
            this.populatePossibleInputs(this.stacksCache);
        }
        return this.stacksCache;
    }

    abstract void populatePossibleInputs(NonNullList<ItemStack> stacks);
}
