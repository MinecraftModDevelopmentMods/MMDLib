package com.mcmoddev.lib.container;

import javax.annotation.Nullable;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Interfaces implemented by things that can have a GUI. Usually a tile entity.
 */
public interface IGuiProvider {
    /**
     * Gets the GuiContainer instance for this gui.
     * @param id The internal gui id asked for.
     * @param player The player is gui is for.
     * @param world The world this gui is for.
     * @param x The x coordinate of the world position this gui is for.
     * @param y The y coordinate of the world position this gui is for.
     * @param z The z coordinate of the world position this gui is for.
     * @return The GuiContainer instance for this gui.
     */
    @Nullable
    @SideOnly(Side.CLIENT)
    GuiContainer getClientGui(int id, EntityPlayer player, World world, int x, int y, int z);

    /**
     * Gets the Container instance for this gui.
     * @param id The internal gui id asked for.
     * @param player The player is gui is for.
     * @param world The world this gui is for.
     * @param x The x coordinate of the world position this gui is for.
     * @param y The y coordinate of the world position this gui is for.
     * @param z The z coordinate of the world position this gui is for.
     * @return The Container instance for this gui.
     */
    @Nullable
    Container getServerGui(int id, EntityPlayer player, World world, int x, int y, int z);
}
