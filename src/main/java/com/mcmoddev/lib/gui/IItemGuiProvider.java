package com.mcmoddev.lib.gui;

import javax.annotation.Nullable;
import com.mcmoddev.lib.container.MMDContainer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public interface IItemGuiProvider {
    enum GuiType {
        BLOCK, AIR
    }

    @Nullable
    default Container getServerGui(GuiType guiType, ItemStack stack, EntityPlayer player, World world, int x, int y, int z) {
        IGuiHolder holder = (!stack.isEmpty() && (stack.getItem() instanceof IGuiHolder))
            ? IGuiHolder.class.cast(stack.getItem())
            : null;

        return (holder == null) ? null : new MMDContainer(holder, player);
    }

    @Nullable
    @SideOnly(Side.CLIENT)
    default GuiContainer getClientGui(GuiType guiType, ItemStack stack, EntityPlayer player, World world, int x, int y, int z) {
        IGuiHolder holder = (!stack.isEmpty() && (stack.getItem() instanceof IGuiHolder))
            ? IGuiHolder.class.cast(stack.getItem())
            : null;

        if (holder != null) {
            Container container = this.getServerGui(guiType, stack, player, world, x, y, z);
            if (container instanceof MMDContainer) {
                return new MMDGuiContainer(holder, player, MMDContainer.class.cast(container));
            }
        }

        return null;
    }
}
