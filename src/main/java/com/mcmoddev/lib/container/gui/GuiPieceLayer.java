package com.mcmoddev.lib.container.gui;

import net.minecraft.client.gui.inventory.GuiContainer;

public enum GuiPieceLayer {
    /**
     * First pass of the {@link GuiContainer#drawGuiContainerBackgroundLayer(float, int, int)} call.
     */
    BACKGROUND,

    /**
     * Second pass of the {@link GuiContainer#drawGuiContainerBackgroundLayer(float, int, int)} call.
     * Useful for drawing stuff that should be above other background things but still below the top layer.
     */
    MIDDLE,

    /**
     * First pass of the {@link GuiContainer#drawGuiContainerForegroundLayer(int, int)} call.
     */
    FOREGROUND,

    /**
     * Second pass of the {@link GuiContainer#drawGuiContainerForegroundLayer(int, int)} call.
     * Useful for drawing stuff like tooltips and such.
     */
    TOP
}
