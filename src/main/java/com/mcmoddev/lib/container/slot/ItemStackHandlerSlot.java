package com.mcmoddev.lib.container.slot;

import java.util.List;
import com.google.common.collect.Lists;
import net.minecraft.inventory.Slot;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

public class ItemStackHandlerSlot extends BaseContainerSlot {
    private final Slot slot;

    public ItemStackHandlerSlot(final IItemHandler stacks, final String featureKey, final int slot) {
        super("self", featureKey);

        this.slot = new FilteredSlot(stacks, slot, 0, 0);
    }

    @Override
    protected Slot getInternalSlot() {
        return this.slot;
    }

    public static List<ItemStackHandlerSlot> createSlots(final IItemHandlerModifiable inventory, final String featureKey) {
        final List<ItemStackHandlerSlot> slots = Lists.newArrayList();

        for(int index = 0; index < inventory.getSlots(); index++) {
            slots.add(new ItemStackHandlerSlot(inventory, featureKey, index));
        }

        return slots;
    }
}
