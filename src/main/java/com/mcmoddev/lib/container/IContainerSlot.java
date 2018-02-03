package com.mcmoddev.lib.container;

import net.minecraft.inventory.Slot;

/**
 * Represents information about a future Slot to be added to a {@link MMDContainer}.
 */
public interface IContainerSlot {
    /**
     * Returns the slot that will be added to the corresponding {@link MMDContainer}.
     * @return The Slot!
     * @implNote This slot instance should be cached and all calls to this method should return the same one.
     */
    Slot getSlot();

    /**
     * This slot's Group Id. Used to transfer items between inventories with shift + click.
     * @return This slot's Group Id.
     */
    String getGroupId();
    /**
     * This slot's SubGroup Id. Used to transfer items between inventories with shift + click.
     * @return This slot's SubGroup Id.
     */
    String getSubGroupId();

    /**
     * The final index of the attached {@link Slot} inside the container.
     * @return The {@link Slot#slotNumber} of the attached slot.
     */
    int getIndex();

    /**
     * Sets the actual index of the attached {@link Slot}.
     * @param index The {@link Slot#slotNumber} of the attached slot.
     */
    void setIndex(int index);
}
