package com.mcmoddev.lib.container;

/**
 * Display information about a {@link PlayerInventory}.
 */
public class PlayerInventoryInfo {
    /**
     * The player inventory.
     */
    public final PlayerInventory inventory;

    /**
     * How many slots per row should be displayed.
     */
    public final int slotsPerRow;

    /**
     * Initializes a new instance of PlayerInventoryInfo.
     * @param inventory The player inventory.
     * @param slotsPerRow How many slots per row should be displayed.
     */
    public PlayerInventoryInfo(final PlayerInventory inventory, final int slotsPerRow) {
        this.inventory = inventory;
        this.slotsPerRow = slotsPerRow;
    }
}
