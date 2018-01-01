package com.mcmoddev.lib.gui;

import net.minecraft.inventory.Slot;

public interface IContainerSlot {
    Slot getSlot();

    String getGroupId();
    String getSubGroupId();
    int getIndex();
}
