package com.mcmoddev.lib.inventory;

import javax.annotation.Nonnull;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

public interface IFilteredItemHandler extends IItemHandler {
    boolean canInsertItem(int slot, @Nonnull ItemStack stack);
    boolean canExtractItem(int slot, int amount);
}
