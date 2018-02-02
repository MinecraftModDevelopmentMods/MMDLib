package com.mcmoddev.lib.container.slots;

import java.util.List;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.container.PlayerInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;

public class PlayerInventorySlot extends BaseContainerSlot {
    private final EntityPlayer player;
    private final PlayerInventory inventory;
    private final int inventoryIndex;

    public PlayerInventorySlot(EntityPlayer player, PlayerInventory inventory, int index) {
        this.player = player;
        this.inventory = inventory;
        this.inventoryIndex = index;
    }

    @Override
    protected Slot getInternalSlot() {
        // position will be set on client thread by the gui layouts
        return new Slot(this.player.inventory, this.inventory.slotStart + this.inventoryIndex, 0, 0);
    }

    public static List<PlayerInventorySlot> createSlots(EntityPlayer player, PlayerInventory inventory) {
        List<PlayerInventorySlot> slots = Lists.newArrayList();

        for(int index = 0; index < inventory.slotCount; index++) {
            slots.add(new PlayerInventorySlot(player, inventory, index));
        }

        return slots;
    }
}
