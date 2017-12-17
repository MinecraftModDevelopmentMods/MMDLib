package com.mcmoddev.lib.gui;

import net.minecraft.item.ItemStack;

public interface IItemStackGuiProvider {
    default IGuiProvider getGuiProvider(ItemStack stack) {
        return new BaseItemStackGuiProvider(stack);
    }
}
