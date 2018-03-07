package com.mcmoddev.lib.container;

/**
 * Represents the set of IDs used by {@link MMDGuiHandler}.
 */
public enum GuiHandlerIds {
    /**
     * Gui ID representing a tile entity.
     */
    GUI_TILE(1),            // 001
    /**
     * Gui ID representing the item in player's main hand.
     */
    GUI_ITEM_MAIN_HAND(2),  // 010
    /**
     * Gui ID representing the item in player's off hand.
     */
    GUI_ITEM_OFF_HAND(3),   // 011
    /**
     * Gui ID representing a command. Not yet implemented.
     */
    GUI_COMMAND(4);         // 100

    private final int value;

    GuiHandlerIds(final int value) {
        this.value = value;
    }

    /**
     * Gets the numerical value of this ID.
     * @return The numerical value of this ID.
     */
    public int getValue() {
        return this.value;
    }
}
