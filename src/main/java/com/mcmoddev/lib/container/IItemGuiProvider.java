package com.mcmoddev.lib.container;

import javax.annotation.Nullable;
import com.mcmoddev.lib.container.gui.MMDGuiContainer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Provides a way to create a GUI for an item stack. Can be implemented by {@link Item Items}.
 */
public interface IItemGuiProvider {
    /**
     * Used to specify the type of GUI to be opened.
     */
    enum GuiType {
        /**
         * The gui should be opened when a block is clicked with this item.
         */
        BLOCK,
        /**
         * The gui should be opened when this item is used on empty air.
         */
        AIR
    }

    /**
     * Gets the {@link Container} instance of this GUI. This is called on both server and client side.
     * @param guiType Type of actioned performed on the item.
     * @param stack The item stack used.
     * @param player The player the GUI should display for.
     * @param world The world the GUI should display for.
     * @param x The x coordinate of the block the item stack was used on.
     * @param y The x coordinate of the block the item stack was used on.
     * @param z The x coordinate of the block the item stack was used on.
     * @return The Container instance of this GUI.
     */
    @Nullable
    default Container getServerGui(final GuiType guiType, final ItemStack stack, final EntityPlayer player, final World world, final int x, final int y, final int z) {
        if (!stack.isEmpty() && (stack.getItem() instanceof IItemGuiProvider)) {
            final IItemGuiProvider provider = IItemGuiProvider.class.cast(stack.getItem());
            if (provider != this) {
                // just in case someone forgot to override the default implementation
                return provider.getServerGui(guiType, stack, player, world, x, y, z);
            }
        }

        final IWidgetContainer holder = (!stack.isEmpty() && (stack.getItem() instanceof IWidgetContainer))
            ? IWidgetContainer.class.cast(stack.getItem())
            : null;

        return (holder == null) ? null : new MMDContainer(holder, player);
    }

    /**
     * Gets the {@link GuiContainer} instance of this GUI. This should only be called/available on client side.
     * @param guiType Type of actioned performed on the item.
     * @param stack The item stack used.
     * @param player The player the GUI should display for.
     * @param world The world the GUI should display for.
     * @param x The x coordinate of the block the item stack was used on.
     * @param y The x coordinate of the block the item stack was used on.
     * @param z The x coordinate of the block the item stack was used on.
     * @return The GuiContainer instance of this GUI.
     */
    @Nullable
    @SideOnly(Side.CLIENT)
    default GuiContainer getClientGui(final GuiType guiType, final ItemStack stack, final EntityPlayer player, final World world, final int x, final int y, final int z) {
        if (!stack.isEmpty() && (stack.getItem() instanceof IItemGuiProvider)) {
            final IItemGuiProvider provider = IItemGuiProvider.class.cast(stack.getItem());
            if (provider != this) {
                // just in case someone forgot to override the default implementation
                return provider.getClientGui(guiType, stack, player, world, x, y, z);
            }
        }

        final IWidgetContainer holder = (!stack.isEmpty() && (stack.getItem() instanceof IWidgetContainer))
            ? IWidgetContainer.class.cast(stack.getItem())
            : null;

        if (holder != null) {
            final Container container = this.getServerGui(guiType, stack, player, world, x, y, z);
            if (container instanceof MMDContainer) {
                return new MMDGuiContainer(holder, player, MMDContainer.class.cast(container));
            }
        }

        return null;
    }
}
