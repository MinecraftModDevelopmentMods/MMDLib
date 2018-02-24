package com.mcmoddev.lib.container.widget;

import java.util.Collection;
import java.util.List;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.container.IContainerSlot;
import com.mcmoddev.lib.container.slot.ItemStackHandlerSlot;
import net.minecraftforge.items.IItemHandlerModifiable;

public class ItemStackHandlerWidget extends BaseWidget {
    private final IItemHandlerModifiable inventory;

    private List<IContainerSlot> cachedSlots = null;

    public ItemStackHandlerWidget(final String key, final IItemHandlerModifiable inventory) {
        super(key,false); // dirty state is handled by the slots themselves

        this.inventory = inventory;
    }

    @Override
    public Collection<IContainerSlot> getSlots() {
        if (this.cachedSlots != null) {
            return this.cachedSlots;
        }

        final List<IContainerSlot> slots = Lists.newArrayList();

        slots.addAll(ItemStackHandlerSlot.createSlots(inventory, this.getKey()));

        return (this.cachedSlots = slots);
    }
}
