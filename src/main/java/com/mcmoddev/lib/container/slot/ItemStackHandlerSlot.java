package com.mcmoddev.lib.container.slot;

import java.util.List;
import com.google.common.collect.Lists;
import net.minecraft.inventory.Slot;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.SlotItemHandler;

public class ItemStackHandlerSlot extends BaseContainerSlot {
    private final Slot slot;

    public ItemStackHandlerSlot(IItemHandler stacks, String featureKey, int slot) {
        super("self", featureKey);

        this.slot = new SlotItemHandler(stacks, slot, 0, 0);
    }

    @Override
    protected Slot getInternalSlot() {
        return this.slot;
    }

    public static List<ItemStackHandlerSlot> createSlots(IItemHandlerModifiable inventory, String featureKey) {
        List<ItemStackHandlerSlot> slots = Lists.newArrayList();

        for(int index = 0; index < inventory.getSlots(); index++) {
            slots.add(new ItemStackHandlerSlot(inventory, featureKey, index));
        }

        return slots;
    }
}
