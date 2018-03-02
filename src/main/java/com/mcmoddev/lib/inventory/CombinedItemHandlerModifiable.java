package com.mcmoddev.lib.inventory;

import javax.annotation.Nonnull;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * Provides {@link IItemHandlerModifiable} support to a {@link CombinedItemHandler}.
 */
public class CombinedItemHandlerModifiable extends CombinedItemHandler implements IItemHandlerModifiable {
    /**
     * Creates a new instance of CombinedItemHandlerModifiable.
     * @param handlers The list of item handlers to be merged.
     */
    public CombinedItemHandlerModifiable(final IItemHandler... handlers) {
        super(handlers);
    }

    @Override
    public void setStackInSlot(final int slot, @Nonnull final ItemStack stack) {
        final ItemHandlerPosition pos = this.findActualSlot(slot);
        if ((pos != null) && (pos.handler instanceof IItemHandlerModifiable)) {
            ((IItemHandlerModifiable) pos.handler).setStackInSlot(pos.slot, stack);
        }
        else {
            throw new RuntimeException("Could not forcibly set the content of slot " + slot + ".");
        }
    }
}
