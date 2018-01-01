package com.mcmoddev.lib.gui;

public class PlayerInventoryInfo {
    public final PlayerInventory inventory;
    public final int guiLeft;
    public final int guiTop;
    public final int slotsPerRow;

    public PlayerInventoryInfo(PlayerInventory inventory, int guiLeft, int guiTop, int slotsPerRow) {
        this.inventory = inventory;
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
        this.slotsPerRow = slotsPerRow;
    }
}
