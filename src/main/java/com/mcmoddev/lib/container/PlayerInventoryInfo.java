package com.mcmoddev.lib.container;

public class PlayerInventoryInfo {
    public final PlayerInventory inventory;
    public final int slotsPerRow;

    public PlayerInventoryInfo(PlayerInventory inventory, int slotsPerRow) {
        this.inventory = inventory;
        this.slotsPerRow = slotsPerRow;
    }
}
