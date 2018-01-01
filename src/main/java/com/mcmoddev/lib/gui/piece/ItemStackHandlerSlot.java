package com.mcmoddev.lib.gui.piece;

import javax.annotation.Nullable;
import com.mcmoddev.lib.gui.IGuiSprite;
import net.minecraft.inventory.Slot;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public class ItemStackHandlerSlot extends BaseInventorySlot {
    private final Slot slot;

    public ItemStackHandlerSlot(IItemHandler stacks, String featureKey, int slot) {
        this(stacks, featureKey, slot, DEFAULT_BG_SPRITE);
    }

    public ItemStackHandlerSlot(IItemHandler stacks, String featureKey, int slot, @Nullable IGuiSprite sprite) {
        super("self", featureKey, slot, sprite);

        this.slot = new SlotItemHandler(stacks, slot, 0, 0);
    }

    @Override
    protected Slot getInternalSlot() {
        return this.slot;
    }
}
