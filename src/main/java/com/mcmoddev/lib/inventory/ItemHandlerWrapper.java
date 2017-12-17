package com.mcmoddev.lib.inventory;

import javax.annotation.Nonnull;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.IItemHandlerModifiable;

public abstract class ItemHandlerWrapper implements IItemHandlerModifiable, INBTSerializable<NBTTagCompound> {
    private final IItemHandlerModifiable internal;

    public ItemHandlerWrapper(IItemHandlerModifiable internal) {
        this.internal = internal;
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        this.internal.setStackInSlot(slot, stack);
        this.onChanged(slot);
    }

    @Override
    public int getSlots() {
        return this.internal.getSlots();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return this.internal.getStackInSlot(slot);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        ItemStack result = this.internal.insertItem(slot, stack, simulate);

        if (!simulate && (result.getCount() != stack.getCount())) {
            this.onChanged(slot);
        }

        return result;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack result = this.internal.extractItem(slot, amount, simulate);

        if (!simulate && !result.isEmpty()) {
            this.onChanged(slot);
        }

        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.internal.getSlotLimit(slot);
    }

    protected abstract void onChanged(int slot);

    @Override
    public NBTTagCompound serializeNBT() {
        //noinspection unchecked
        return (this.internal instanceof INBTSerializable)
            ? ((INBTSerializable<NBTTagCompound>)this.internal).serializeNBT()
            : new NBTTagCompound();
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        if (this.internal instanceof INBTSerializable) {
            // TODO: also make sure to apply filters and such
            //noinspection unchecked
            ((INBTSerializable<NBTTagCompound>) this.internal).deserializeNBT(nbt);
        }
    }
}
