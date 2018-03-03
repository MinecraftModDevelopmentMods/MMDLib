package com.mcmoddev.lib.container;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Implemented by {@link Item Items} that can provide a GUI.
 */
public interface IItemStackGuiProvider {
    /**
     * Returns a {@link IGuiProvider gui provider} for the specified item stack.
     * @param stack The item stack to open the gui for.
     * @return The gui provider corresponding to the specified item stack.
     */
    default IGuiProvider getGuiProvider(final ItemStack stack) {
        return new BaseItemStackWidgetProvider(stack);
    }
}
