package com.mcmoddev.lib.container;

import net.minecraft.item.ItemStack;

public interface IItemStackGuiProvider {
    default IGuiProvider getGuiProvider(ItemStack stack) {
        return new BaseItemStackWidgetProvider(stack);
    }
}
