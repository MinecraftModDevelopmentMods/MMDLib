package com.mcmoddev.lib.container;

import net.minecraft.inventory.Slot;

public interface IContainerSlot {
    Slot getSlot();

    String getGroupId();
    String getSubGroupId();

    int getIndex();
    void setIndex(int index);
}
