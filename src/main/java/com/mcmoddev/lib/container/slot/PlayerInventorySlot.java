package com.mcmoddev.lib.container.slot;

import java.util.List;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.container.IContainerSlot;
import com.mcmoddev.lib.container.PlayerInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;

/**
 * An {@link IContainerSlot} that provides a {@link Slot} linked to a {@link PlayerInventory}.
 */
public class PlayerInventorySlot extends BaseContainerSlot {
    private final EntityPlayer player;
    private final PlayerInventory inventory;
    private final int inventoryIndex;

    /**
     * Initializes a new instance of PlayerInventorySlot.
     * @param player The player owning the inventory.
     * @param inventory The player inventory piece.
     * @param index The local index inside this inventory piece.
     */
    public PlayerInventorySlot(final EntityPlayer player, final PlayerInventory inventory, final int index) {
        this.player = player;
        this.inventory = inventory;
        this.inventoryIndex = index;
    }

    @Override
    protected Slot getInternalSlot() {
        // position will be set on client thread by the gui layouts
        return new Slot(this.player.inventory, this.inventory.slotStart + this.inventoryIndex, 0, 0);
    }

    /**
     * Gets a list of container slots for an entire player inventory piece.
     * @param player The player owning the inventory.
     * @param inventory The player inventory piece.
     * @return A list of container slots for an entire player inventory piece.
     */
    public static List<PlayerInventorySlot> createSlots(final EntityPlayer player, final PlayerInventory inventory) {
        final List<PlayerInventorySlot> slots = Lists.newArrayList();

        for(int index = 0; index < inventory.slotCount; index++) {
            slots.add(new PlayerInventorySlot(player, inventory, index));
        }

        return slots;
    }
}
