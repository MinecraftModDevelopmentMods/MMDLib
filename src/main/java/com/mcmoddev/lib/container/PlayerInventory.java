package com.mcmoddev.lib.container;

public enum PlayerInventory {
    INVENTORY("_player", 9, 27, false),
    QUICKBAR("_quickbar", 0, 9, true),
    EQUIPMENT("_equipment", 36, 4, false),
    OFF_HAND("_shield", 40, 1, false);

    public final String key;
    public final int slotStart;
    public final int slotEndExclusive;
    public final int slotCount;
    public final boolean insertInReverse;

    PlayerInventory(final String key, final int slotStart, final int slotCount, final boolean insertInReverse) {
        this.key = key;
        this.slotStart = slotStart;
        this.slotCount = slotCount;
        this.slotEndExclusive = slotStart + slotCount;
        this.insertInReverse = insertInReverse;
    }
}
