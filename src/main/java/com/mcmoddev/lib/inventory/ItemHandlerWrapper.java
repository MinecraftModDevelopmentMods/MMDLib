package com.mcmoddev.lib.inventory;

import javax.annotation.Nonnull;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * Provides a serializable wrapper for a {@link IItemHandlerModifiable} which also has a method to notify about content being changed.
 */
@SuppressWarnings("unchecked")
public abstract class ItemHandlerWrapper implements IItemHandlerModifiable, INBTSerializable<NBTTagCompound> {
    private final IItemHandlerModifiable internal;

    /**
     * Creates a new instance of {@link ItemHandlerWrapper}.
     * @param internal The {@link IItemHandlerModifiable} to be wrapped.
     */
    public ItemHandlerWrapper(final IItemHandlerModifiable internal) {
        this.internal = internal;
    }

    @Override
    public void setStackInSlot(final int slot, @Nonnull final ItemStack stack) {
        this.internal.setStackInSlot(slot, stack);
        this.onChanged(slot);
    }

    @Override
    public int getSlots() {
        return this.internal.getSlots();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(final int slot) {
        return this.internal.getStackInSlot(slot);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(final int slot, @Nonnull final ItemStack stack, final boolean simulate) {
        final ItemStack result = this.internal.insertItem(slot, stack, simulate);

        if (!simulate && (result.getCount() != stack.getCount())) {
            this.onChanged(slot);
        }

        return result;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
        final ItemStack result = this.internal.extractItem(slot, amount, simulate);

        if (!simulate && !result.isEmpty()) {
            this.onChanged(slot);
        }

        return result;
    }

    @Override
    public int getSlotLimit(final int slot) {
        return this.internal.getSlotLimit(slot);
    }

    /**
     * Called when this item handler's content changes.
     * @param slot The slot that was changed.
     */
    protected abstract void onChanged(int slot);

    @Override
    public NBTTagCompound serializeNBT() {
        //noinspection unchecked
        return (this.internal instanceof INBTSerializable)
            ? ((INBTSerializable<NBTTagCompound>)this.internal).serializeNBT()
            : new NBTTagCompound();
    }

    @Override
    public void deserializeNBT(final NBTTagCompound nbt) {
        if (this.internal instanceof INBTSerializable) {
            // TODO: also make sure to apply filters and such
            //noinspection unchecked
            ((INBTSerializable<NBTTagCompound>) this.internal).deserializeNBT(nbt);
        }
    }
}
