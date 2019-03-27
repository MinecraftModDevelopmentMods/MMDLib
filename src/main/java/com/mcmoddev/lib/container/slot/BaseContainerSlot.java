package com.mcmoddev.lib.container.slot;

import com.mcmoddev.lib.container.IContainerSlot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

/**
 * Basic implementation of {@link IContainerSlot}.
 */
public abstract class BaseContainerSlot implements IContainerSlot {
    private int index;

    private final String groupId;
    private final String subGroupId;

    private Slot cachedSlot;

    /**
     * Initializes a new instance of BaseContainerSlot. Uses an empty string for groupId and subGroupId.
     */
    protected BaseContainerSlot() {
        this("", "");
    }

    /**
     * Initializes a new instance of BaseContainerSlot. Uses an empty string for subGroupId.
     * @param groupId The group id of this slot. Group/subgroups are used for {@link Container#transferStackInSlot(EntityPlayer, int)}.
     */
    protected BaseContainerSlot(final String groupId) {
        this(groupId, "");
    }

    /**
     * Initializes a new instance of BaseContainerSlot. Uses an empty string for subGroupId.
     * @param groupId The group id of this slot. Group/subgroups are used for {@link Container#transferStackInSlot(EntityPlayer, int)}.
     * @param subGroupId The sub group id of this slot. Group/subgroups are used for {@link Container#transferStackInSlot(EntityPlayer, int)}.
     */
    protected BaseContainerSlot(final String groupId, final String subGroupId) {
        this.groupId = groupId;
        this.subGroupId = subGroupId;
    }

    @Override
    public String getGroupId() {
        return this.groupId;
    }

    @Override
    public String getSubGroupId() {
        return this.subGroupId;
    }

    @Override
    public int getIndex() {
        return this.index;
    }

    @Override
    public void setIndex(final int index) {
        this.index = index;
    }

    @Override
    public final Slot getSlot() {
        if (this.cachedSlot == null) {
            this.cachedSlot = this.getInternalSlot();
        }
        return this.cachedSlot;
    }

    /**
     * Creates the actual Slot exported by this container slot.
     * @return The actual Slot exported by this container slot.
     */
    protected abstract Slot getInternalSlot();
}
