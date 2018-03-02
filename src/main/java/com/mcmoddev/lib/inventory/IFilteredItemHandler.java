package com.mcmoddev.lib.inventory;

import javax.annotation.Nonnull;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * Provides support for filtering what goes in/out of an {@link IItemHandler}.
 */
public interface IFilteredItemHandler extends IItemHandler {
    /**
     * Tests if the specified item can be inserted at the desired position.
     * @param slot The position where the specified item should be inserted.
     * @param stack The item that should be inserted
     * @return True if the item can be inserted at the specified position. False otherwise.
     */
    boolean canInsertItem(int slot, @Nonnull ItemStack stack);

    /**
     * Tests if the specified amount of items can be extracted from the desired position.
     * @param slot The position the items should be extracted from.
     * @param amount The amount of items that should be extracted.
     * @return True if the desired amount of items can be extracted from the specified position. False otherwise.
     */
    boolean canExtractItem(int slot, int amount);
}
