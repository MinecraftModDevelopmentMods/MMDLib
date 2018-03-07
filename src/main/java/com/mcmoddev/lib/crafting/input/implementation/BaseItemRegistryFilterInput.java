package com.mcmoddev.lib.crafting.input.implementation;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

public abstract class BaseItemRegistryFilterInput extends BaseItemInput {
    protected final int stackMeta;

    protected BaseItemRegistryFilterInput(String key, int stackCount, int stackMeta) {
        super(key, stackCount);

        this.stackMeta = stackMeta;
    }

    @Override
    final void populatePossibleInputs(NonNullList<ItemStack> stacks) {
        Item.REGISTRY.forEach(item -> {
            if (this.isMatch(item)) {
                stacks.add(this.createStack(item));
            }
        });
    }

    protected ItemStack createStack(Item item) {
        return new ItemStack(item, this.getAmount(), this.stackMeta);
    }

    abstract boolean isMatch(Item item);

    // TODO: maybe move this to a generic helper somewhere
    protected static boolean isNameMatch(String pattern, String name) {
        boolean startWild = false;
        boolean endWild = false;
        if (pattern.startsWith("*")) {
            startWild = true;
            pattern = pattern.substring(1);
        }
        if (pattern.endsWith("*")) {
            endWild = true;
            if (pattern.length() > 1) {
                pattern = pattern.substring(0, pattern.length() - 2);
            }
            else pattern = "";
        }

        pattern = pattern.toLowerCase();
        name = name.toLowerCase();

        if (!startWild && !endWild) {
            return name.equals(pattern);
        }
        else if (startWild && !endWild) {
            return name.endsWith(pattern);
        }
        else if (!startWild /* && endWild */) {
            return name.startsWith(pattern);
        }
        else /* if (startWild && endWild) */ {
            return name.contains(pattern);
        }
    }
}
