package com.mcmoddev.lib.inventory;

import java.util.function.BiPredicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.mcmoddev.lib.util.ItemStackUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

public class FilteredItemHandler implements IFilteredItemHandler, IItemHandlerModifiable {
    private final IItemHandlerModifiable internal;
    @Nullable
    private final BiPredicate<Integer, ItemStack> insertFilter;
    @Nullable
    private final BiPredicate<Integer, ItemStack> extractFilter;

    public FilteredItemHandler(IItemHandlerModifiable internal,
                               @Nullable BiPredicate<Integer, ItemStack> insertFilter,
                               @Nullable BiPredicate<Integer, ItemStack> extractFilter) {
        this.internal = internal;
        this.insertFilter = insertFilter;
        this.extractFilter = extractFilter;
    }

    @Override
    public boolean canInsertItem(int slot, @Nonnull ItemStack stack) {
        return (this.insertFilter == null) || this.insertFilter.test(slot, stack);
    }

    @Override
    public boolean canExtractItem(int slot, int amount) {
        if (amount == 0) {
            return true;
        }

        ItemStack stack = this.getStackInSlot(slot);
        return !stack.isEmpty() && ((this.extractFilter == null) || this.extractFilter.test(slot, ItemStackUtils.copyWithSize(stack, amount)));
    }

    @Override
    public int getSlots() {
        return this.internal.getSlots();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        // TODO: figure out if filter needs to be applied here
        return this.internal.getStackInSlot(slot);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (!this.canInsertItem(slot, stack)) {
            return stack;
        }

        return this.internal.insertItem(slot, stack, simulate);
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!this.canExtractItem(slot, amount)) {
            return ItemStack.EMPTY;
        }
        return this.internal.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.internal.getSlotLimit(slot);
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        if (!this.canInsertItem(slot, stack)) {
            throw new RuntimeException("Cannot set slot " + slot + " to an unacceptable stack.");
        }
        this.internal.setStackInSlot(slot, stack);
    }
}
