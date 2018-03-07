package com.mcmoddev.lib.container;

/**
 * Helper enum for referencing the various pieces of a player's inventory.
 */
public enum PlayerInventory {
    /**
     * The main inventory part.
     */
    INVENTORY("player", 9, 27, false),
    /**
     * The 9 quick bar slots.
     */
    QUICKBAR("quickbar", 0, 9, true),
    /**
     * the equipment (ie. armor) slots.
     */
    EQUIPMENT("equipment", 36, 4, false),
    /**
     * The shield slot.
     */
    OFF_HAND("shield", 40, 1, false);

    /**
     * Key the uniquely identifies this player inventory piece.
     */
    public final String key;
    /**
     * The first slot in the player's inventory corresponding to this piece.
     */
    public final int slotStart;
    /**
     * The last slot (exclusive last) in the player's inventory corresponding to this piece.
     */
    public final int slotEndExclusive;
    /**
     * Number of slots in this player inventory piece.
     */
    public final int slotCount;
    /**
     * Flags specifying if items should be inserted here from end to start. Used mainly by the Shift+Click handling in GUIs.
     */
    public final boolean insertInReverse;

    PlayerInventory(final String key, final int slotStart, final int slotCount, final boolean insertInReverse) {
        this.key = key;
        this.slotStart = slotStart;
        this.slotCount = slotCount;
        this.slotEndExclusive = slotStart + slotCount;
        this.insertInReverse = insertInReverse;
    }
}
