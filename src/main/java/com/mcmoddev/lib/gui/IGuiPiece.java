package com.mcmoddev.lib.gui;

public interface IGuiPiece {
    int getLeft();
    int getTop();
    int getWidth();
    int getHeight();

    default boolean getHandlesGlobalMouseEvents() { return false; }

    boolean mouseClicked(MMDGuiContainer container, int mouseX, int mouseY, int mouseButton);
    boolean mouseClickMove(MMDGuiContainer container, int mouseX, int mouseY, int mouseButton);
    boolean mouseReleased(MMDGuiContainer container, int mouseX, int mouseY, int mouseButton);

    void drawBackgroundLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY);
    void drawMiddleLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY);
    void drawForegroundLayer(MMDGuiContainer container, int mouseX, int mouseY);
    void drawForegroundTopLayer(MMDGuiContainer container, int mouseX, int mouseY);

    default boolean isVisible() { return true; }
    boolean setVisibility(boolean isVisible);
}
