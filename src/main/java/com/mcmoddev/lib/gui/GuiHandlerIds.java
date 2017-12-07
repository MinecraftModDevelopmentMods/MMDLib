package com.mcmoddev.lib.gui;

public enum GuiHandlerIds {
    GUI_TILE(1),            // 001
    GUI_ITEM_MAIN_HAND(2),  // 010
    GUI_ITEM_OFF_HAND(3),   // 011
    GUI_COMMAND(4);         // 100

    private int value;

    GuiHandlerIds(int value) {
        this.value = value;
    }

    public int getValue() {
        return this.value;
    }
}
