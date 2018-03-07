package com.mcmoddev.lib.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * Class that can be used to combine multiple {@link IItemHandler IItemHandlers} together.
 */
@SuppressWarnings("WeakerAccess")
public class CombinedItemHandler implements IItemHandler {
    /**
     * The internal list of item handlers.
     */
    protected final List<IItemHandler> handlers = new ArrayList<>();

    /**
     * Creates a new instance of CombinedItemHandler.
     * @param handlers The list of item handlers to be merged.
     */
    public CombinedItemHandler(final IItemHandler... handlers) {
        if (handlers.length > 0) {
            this.handlers.addAll(Arrays.asList(handlers));
        }
    }

    /**
     * Adds a new item handler to the combined inventory.
     * @param handler The item handler to be merged.
     */
    public void addItemHandler(final IItemHandler handler) {
        this.handlers.add(handler);
    }

    /**
     * Finds the local slot information for a merged item handler.
     * @param slotIn The global slot index.
     * @return slot information for the corresponding item handler. Or null if the slot is out of range.
     */
    @Nullable
    protected ItemHandlerPosition findActualSlot(final int slotIn) {
        int slots = 0;
        int handlerIndex = 0;
        while ((slots <= slotIn) && (handlerIndex < this.handlers.size())) {
            final int local = slotIn - slots;
            final IItemHandler handler = this.handlers.get(handlerIndex);
            if (local < handler.getSlots()) {
                return new ItemHandlerPosition(handler, local);
            }

            slots += handler.getSlots();
            handlerIndex++;
        }
        return null;
    }

    @Override
    public int getSlots() {
        return this.handlers.stream().mapToInt(IItemHandler::getSlots).sum();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(final int slot) {
        final ItemHandlerPosition pos = this.findActualSlot(slot);
        return (pos != null) ? pos.handler.getStackInSlot(pos.slot) : ItemStack.EMPTY;
    }

    @Nonnull
    @Override
    public ItemStack insertItem(final int slot, @Nonnull final ItemStack stack, final boolean simulate) {
        final ItemHandlerPosition pos = this.findActualSlot(slot);
        return (pos == null) ? stack : pos.handler.insertItem(pos.slot, stack, simulate);
    }

    @Nonnull
    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
        final ItemHandlerPosition pos = this.findActualSlot(slot);
        return (pos == null) ? ItemStack.EMPTY : pos.handler.extractItem(pos.slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(final int slot) {
        final ItemHandlerPosition pos = this.findActualSlot(slot);
        return (pos == null) ? 0 : pos.handler.getSlotLimit(pos.slot);
    }

    /**
     * Provides information about a merged item handler's slot.
     */
    protected static class ItemHandlerPosition {
        /**
         * The merged item handler.
         */
        final IItemHandler handler;
        /**
         * The slot this instance refers to.
         */
        final int slot;

        ItemHandlerPosition(final IItemHandler handler, final int slot) {
            this.handler = handler;
            this.slot = slot;
        }
    }
}
