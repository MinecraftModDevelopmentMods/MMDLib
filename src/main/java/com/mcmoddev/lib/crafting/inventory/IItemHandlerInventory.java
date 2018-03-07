package com.mcmoddev.lib.crafting.inventory;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

public interface IItemHandlerInventory extends IItemInventory {
    IItemHandler getItemHandler();

    @Override
    default int getSlots() {
        return this.getItemHandler().getSlots();
    }

    @Override
    default ItemStack getSlotContent(int slot) {
        return this.getItemHandler().getStackInSlot(slot);
    }

    @Override
    default boolean setSlotContent(int slot, ItemStack stack) {
        IItemHandler handler = this.getItemHandler();
        if (handler instanceof IItemHandlerModifiable) {
            ((IItemHandlerModifiable)handler).setStackInSlot(slot, stack);
            return true;
        }
        else {
            ItemStack result = handler.insertItem(slot, stack, true);
            if (result.isEmpty()) {
                handler.insertItem(slot, stack, false);
                return true;
            }
        }
        return false;
    }
}
