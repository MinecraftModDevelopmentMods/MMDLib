package com.mcmoddev.lib.container.slot;

import javax.annotation.Nonnull;
import com.mcmoddev.lib.inventory.IFilteredItemHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public class FilteredSlot extends SlotItemHandler {
    private final int index;

    public FilteredSlot(final IItemHandler itemHandler, final int index, final int xPosition, final int yPosition) {
        super(itemHandler, index, xPosition, yPosition);

        this.index = index;
    }

    @Override
    public boolean canTakeStack(final EntityPlayer playerIn) {
        boolean canTake = true;
        if (this.getItemHandler() instanceof IFilteredItemHandler) {
            final ItemStack stack = this.getStack();
            canTake = stack.isEmpty() || ((IFilteredItemHandler)this.getItemHandler()).canExtractItem(this.index, stack.getCount());
        }
        return canTake && super.canTakeStack(playerIn);
    }

    @Override
    public boolean isItemValid(@Nonnull final ItemStack stack) {
        boolean isValid = true;
        if (this.getItemHandler() instanceof IFilteredItemHandler) {
            isValid = stack.isEmpty() || ((IFilteredItemHandler)this.getItemHandler()).canInsertItem(this.index, stack);
        }
        return isValid && super.isItemValid(stack);
    }
}
