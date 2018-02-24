package com.mcmoddev.lib.inventory;

import javax.annotation.Nonnull;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

public class CombinedItemHandlerModifiable extends CombinedItemHandler implements IItemHandlerModifiable {
    public CombinedItemHandlerModifiable(final IItemHandler... handlers) {
        super(handlers);
    }

    @Override
    public void setStackInSlot(final int slot, @Nonnull final ItemStack stack) {
        final ItemHandlerPosition pos = this.findActualSlot(slot);
        if ((pos != null) && (pos.handler instanceof IItemHandlerModifiable)) {
            ((IItemHandlerModifiable) pos.handler).setStackInSlot(pos.slot, stack);
        }
        // TODO: else { maybe throw an error or something? }
    }
}
