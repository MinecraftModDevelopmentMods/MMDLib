package com.mcmoddev.lib.crafting.inventory;

import javax.annotation.Nullable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

public interface IFluidHandlerInventory extends IFluidInventory {
    IFluidHandler getFluidHandler();

    @Override
    default int getSlots() {
        // use this because 'sometimes' getTankProperties() will return an empty list
        IFluidTankProperties[] tanks = this.getFluidHandler().getTankProperties();
        return ((tanks == null) || (tanks.length == 0)) ? 1 : tanks.length;
    }

    @Nullable
    @Override
    default FluidStack getSlotContent(int slot) {
        IFluidTankProperties[] tanks = this.getFluidHandler().getTankProperties();
        return ((tanks == null) || (tanks.length > slot))
                ? ((slot == 0) ? this.getFluidHandler().drain(Integer.MAX_VALUE, false) : null)
                : tanks[slot].getContents();
    }

    @Override
    default boolean setSlotContent(int slot, FluidStack stack) {
        IFluidHandler handler = this.getFluidHandler();
        int filled = handler.fill(stack, false);
        if (filled == stack.amount) {
            handler.fill(stack, true);
            return true;
        }
        return false;
    }
}
