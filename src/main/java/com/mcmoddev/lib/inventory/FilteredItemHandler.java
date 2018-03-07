package com.mcmoddev.lib.inventory;

import java.util.function.BiPredicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.mcmoddev.lib.util.ItemStackUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * Basic implementation of {@link IFilteredItemHandler}.
 */
public class FilteredItemHandler implements IFilteredItemHandler, IItemHandlerModifiable {
    private final IItemHandlerModifiable internal;
    @Nullable
    private final BiPredicate<Integer, ItemStack> insertFilter;
    @Nullable
    private final BiPredicate<Integer, ItemStack> extractFilter;

    /**
     * Created a new instance of FilteredItemHandler.
     * @param internal The item handler to be filtered.
     * @param insertFilter Predicate used to test if an item can be inserted.
     *                     A null value means you can always insert into the inner item handler.
     * @param extractFilter Predicate used to test if an item can ve extracted.
     *                      A null value means you can always extract from the inner item handler.
     */
    public FilteredItemHandler(final IItemHandlerModifiable internal,
                               @Nullable final BiPredicate<Integer, ItemStack> insertFilter,
                               @Nullable final BiPredicate<Integer, ItemStack> extractFilter) {
        this.internal = internal;
        this.insertFilter = insertFilter;
        this.extractFilter = extractFilter;
    }

    @Override
    public boolean canInsertItem(final int slot, @Nonnull final ItemStack stack) {
        return (this.insertFilter == null) || this.insertFilter.test(slot, stack);
    }

    @Override
    public boolean canExtractItem(final int slot, final int amount) {
        if (amount == 0) {
            return true;
        }

        final ItemStack stack = this.getStackInSlot(slot);
        return !stack.isEmpty() && ((this.extractFilter == null) || this.extractFilter.test(slot, ItemStackUtils.copyWithSize(stack, amount)));
    }

    @Override
    public int getSlots() {
        return this.internal.getSlots();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(final int slot) {
        // TODO: figure out if filter needs to be applied here
        return this.internal.getStackInSlot(slot);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(final int slot, @Nonnull final ItemStack stack, final boolean simulate) {
        if (!this.canInsertItem(slot, stack)) {
            return stack;
        }

        return this.internal.insertItem(slot, stack, simulate);
    }

    @Nonnull
    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
        if (!this.canExtractItem(slot, amount)) {
            return ItemStack.EMPTY;
        }
        return this.internal.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(final int slot) {
        return this.internal.getSlotLimit(slot);
    }

    @Override
    public void setStackInSlot(final int slot, @Nonnull final ItemStack stack) {
        if (!stack.isEmpty() && !this.canInsertItem(slot, stack)) {
            throw new RuntimeException("Cannot set slot " + slot + " to an unacceptable stack.");
        }
        this.internal.setStackInSlot(slot, stack);
    }
}
