package com.mcmoddev.lib.gui.piece;

import com.mcmoddev.lib.gui.PlayerInventory;
import net.minecraft.entity.player.EntityPlayer;

public class PlayerInventoryGrid extends BaseInventoryGrid {
    public PlayerInventoryGrid(EntityPlayer player, PlayerInventory inventory, int columns) {
        super(columns,
            () -> inventory.slotCount,
            (index) -> new PlayerInventorySlot(player, inventory, inventory.slotStart + index)
        );
    }
}
