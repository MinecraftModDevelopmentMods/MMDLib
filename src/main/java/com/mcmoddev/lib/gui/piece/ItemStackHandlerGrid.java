package com.mcmoddev.lib.gui.piece;

import net.minecraftforge.items.IItemHandler;

public class ItemStackHandlerGrid extends BaseInventoryGrid {
    public ItemStackHandlerGrid(IItemHandler stacks, String featureKey, int columns) {
        super(columns,
            stacks::getSlots,
            (index) -> new ItemStackHandlerSlot(stacks, featureKey, index));
    }
}
