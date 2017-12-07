package com.mcmoddev.lib.container;

import java.util.List;
import net.minecraft.inventory.Slot;

public interface IContainerSlotsProvider {
    List<Slot> getContainerSlots(MMDContainer container);
}
