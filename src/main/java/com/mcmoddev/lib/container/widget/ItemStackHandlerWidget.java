package com.mcmoddev.lib.container.widget;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.container.IContainerSlot;
import com.mcmoddev.lib.container.slot.ItemStackHandlerSlot;
import com.mcmoddev.lib.inventory.IFilteredItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
  Widget that provides slots for an {@link IItemHandlerModifiable item handler}.
 */
public class ItemStackHandlerWidget extends BaseWidget {
    private final IItemHandlerModifiable inventory;
    private final IFilteredItemHandler filterInventory;

    private List<IContainerSlot> cachedSlots = null;

    /**
     * Initializes a new instance of ItemStackHandlerWidget.
     * @param key The key that uniquely identifies this widget.
     * @param inventory The inventory this widget handles.
     * @param filterInventory The filtered item handler used to filter acceptable stacks for the slots.
     */
    public ItemStackHandlerWidget(final String key, final IItemHandlerModifiable inventory, @Nullable final IFilteredItemHandler filterInventory) {
        super(key,false); // dirty state is handled by the slots themselves

        this.inventory = inventory;
        this.filterInventory = filterInventory;
    }

    @Override
    public Collection<IContainerSlot> getSlots() {
        if (this.cachedSlots != null) {
            return this.cachedSlots;
        }

        final List<IContainerSlot> slots = Lists.newArrayList();

        slots.addAll(ItemStackHandlerSlot.createSlots(this.inventory, this.filterInventory, this.getKey()));

        return (this.cachedSlots = slots);
    }
}
