package com.mcmoddev.lib.container.slot;

import java.util.List;
import javax.annotation.Nullable;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.container.IContainerSlot;
import com.mcmoddev.lib.inventory.IFilteredItemHandler;
import net.minecraft.inventory.Slot;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * An {@link IContainerSlot} that provides a {@link Slot} linked to an {@link IItemHandler}.
 * This uses {@link FilteredSlot filtered slots} so that filters set on item handlers will work here too.
 */
public class ItemStackHandlerSlot extends BaseContainerSlot {
    private final Slot slot;

    /**
     * Initializes a new instance of ItemStackHandlerSlot.
     * @param stacks The item handler that provide access to the item stack.
     * @param filterInventory The filtered item handler used to filter acceptable stacks for this slot.
     * @param featureKey The key of the feature containing this item handler.
     * @param slot The slot index inside the item handler.
     */
    public ItemStackHandlerSlot(final IItemHandler stacks, @Nullable final IFilteredItemHandler filterInventory, final String featureKey, final int slot) {
        super("self", featureKey);

        this.slot = new FilteredSlot(stacks, filterInventory, slot, 0, 0);
    }

    @Override
    protected Slot getInternalSlot() {
        return this.slot;
    }

    /**
     * Creates a list of container slots for an item handler.
     * @param inventory The item handler providing access to all the item stacks.
     * @param filterInventory The filtered item handler used to filter acceptable stacks for this slot.
     * @param featureKey The key of the feature containing the item handler.
     * @return List of container slots for an item handler.
     */
    public static List<ItemStackHandlerSlot> createSlots(final IItemHandlerModifiable inventory, @Nullable final IFilteredItemHandler filterInventory, final String featureKey) {
        final List<ItemStackHandlerSlot> slots = Lists.newArrayList();

        for(int index = 0; index < inventory.getSlots(); index++) {
            slots.add(new ItemStackHandlerSlot(inventory, filterInventory, featureKey, index));
        }

        return slots;
    }
}
