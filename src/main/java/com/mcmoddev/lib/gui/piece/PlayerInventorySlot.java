package com.mcmoddev.lib.gui.piece;

import javax.annotation.Nullable;
import com.mcmoddev.lib.gui.IGuiSprite;
import com.mcmoddev.lib.gui.PlayerInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;

public class PlayerInventorySlot extends BaseInventorySlot {
    private final Slot slot;

    public PlayerInventorySlot(EntityPlayer player, PlayerInventory inventory, int slot) {
        this(player, inventory, slot, DEFAULT_BG_SPRITE);
    }

    public PlayerInventorySlot(EntityPlayer player, PlayerInventory inventory, int slot, @Nullable IGuiSprite sprite) {
        super("player", inventory.name().toLowerCase(), slot, sprite);

        this.slot = new Slot(player.inventory, slot, 0, 0);
    }

    @Override
    protected Slot getInternalSlot() {
        return this.slot;
    }
}
