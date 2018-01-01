package com.mcmoddev.lib.util;

import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.item.ItemStack;
import mcp.MethodsReturnNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ItemStackUtils {
    private ItemStackUtils() {}

    public static boolean areEqualIgnoreSize(ItemStack a, ItemStack b) {
        if (!ItemStack.areItemStackTagsEqual(a, b)) {
            return false;
        }

        return ItemStack.areItemsEqual(a, b);
    }

    public static ItemStack copyWithSize(ItemStack source, int size) {
        // for some reason I think there is a forge method for this, but couldn't find it :S
        if (size > 0) {
            ItemStack result = source.copy();
            result.setCount(size);
            return result;
        }
        else {
            return ItemStack.EMPTY;
        }
    }
}
