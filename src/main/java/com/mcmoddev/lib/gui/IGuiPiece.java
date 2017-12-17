package com.mcmoddev.lib.gui;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public interface IGuiPiece {
    int getLeft();
    int getTop();
    int getWidth();
    int getHeight();

    default boolean getHandlesGlobalMouseEvents() { return false; }

    default boolean mouseClicked(MMDGuiContainer container, int mouseX, int mouseY, int mouseButton) { return false; }
    default boolean mouseClickMove(MMDGuiContainer container, int mouseX, int mouseY, int mouseButton) { return false; }
    default boolean mouseReleased(MMDGuiContainer container, int mouseX, int mouseY, int mouseButton) { return false; }

    @SideOnly(Side.CLIENT)
    default void drawBackgroundLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {}
    @SideOnly(Side.CLIENT)
    default void drawMiddleLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {}
    @SideOnly(Side.CLIENT)
    default void drawForegroundLayer(MMDGuiContainer container, int mouseX, int mouseY) {}
    @SideOnly(Side.CLIENT)
    default void drawForegroundTopLayer(MMDGuiContainer container, int mouseX, int mouseY) {}

    default boolean isVisible() { return true; }
    boolean setVisibility(boolean isVisible);
}
