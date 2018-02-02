package com.mcmoddev.lib.container.slots;

import com.mcmoddev.lib.container.IContainerSlot;
import net.minecraft.inventory.Slot;

public abstract class BaseContainerSlot implements IContainerSlot {
    private int index;

    private String groupId;
    private String subGroupId;

    private Slot cachedSlot;

    protected BaseContainerSlot() {
        this("", "");
    }

    protected BaseContainerSlot(String groupId) {
        this(groupId, "");
    }

    protected BaseContainerSlot(String groupId, String subGroupId) {
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
    public void setIndex(int index) {
        this.index = index;
    }

    @Override
    public final Slot getSlot() {
        if (this.cachedSlot == null) {
            this.cachedSlot = this.getInternalSlot();
        }
        return this.cachedSlot;
    }

    protected abstract Slot getInternalSlot();
}
