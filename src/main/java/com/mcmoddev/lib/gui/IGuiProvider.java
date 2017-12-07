package com.mcmoddev.lib.gui;

import javax.annotation.Nullable;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.world.World;

public interface IGuiProvider {
    @Nullable
    GuiContainer getClientGui(int id, EntityPlayer player, World world, int x, int y, int z);
    @Nullable
    Container getServerGui(int id, EntityPlayer player, World world, int x, int y, int z);
}
