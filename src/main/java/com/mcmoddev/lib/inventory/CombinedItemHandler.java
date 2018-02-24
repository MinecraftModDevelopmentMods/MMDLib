package com.mcmoddev.lib.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

public class CombinedItemHandler implements IItemHandler {
    protected final List<IItemHandler> handlers = new ArrayList<>();

    public CombinedItemHandler(final IItemHandler... handlers) {
        if (handlers.length > 0) {
            this.handlers.addAll(Arrays.asList(handlers));
        }
    }

    public void addItemHandler(final IItemHandler handler) {
        this.handlers.add(handler);
    }

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

    protected static class ItemHandlerPosition {
        final IItemHandler handler;
        final int slot;

        ItemHandlerPosition(final IItemHandler handler, final int slot) {
            this.handler = handler;
            this.slot = slot;
        }
    }
}
