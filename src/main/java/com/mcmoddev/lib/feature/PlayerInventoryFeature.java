package com.mcmoddev.lib.feature;

import com.mcmoddev.lib.gui.GuiContext;
import com.mcmoddev.lib.gui.IGuiPiece;
import com.mcmoddev.lib.gui.IGuiPieceProvider;
import com.mcmoddev.lib.gui.PlayerInventory;
import com.mcmoddev.lib.gui.PlayerInventoryInfo;
import com.mcmoddev.lib.gui.piece.PlayerInventoryGrid;
import net.minecraft.nbt.NBTTagCompound;

public class PlayerInventoryFeature extends BaseFeature implements IGuiPieceProvider {
    private final PlayerInventoryInfo inventoryInfo;

    public PlayerInventoryFeature(PlayerInventory inventory, int guiLeft, int guiTop, int slotsPerRow) {
        this(new PlayerInventoryInfo(inventory, guiLeft, guiTop, slotsPerRow));
    }

    public PlayerInventoryFeature(String key, PlayerInventory inventory, int guiLeft, int guiTop, int slotsPerRow) {
        this(key, new PlayerInventoryInfo(inventory, guiLeft, guiTop, slotsPerRow));
    }

    public PlayerInventoryFeature(PlayerInventoryInfo inventoryInfo) {
        this("player_" + inventoryInfo.inventory.name().toLowerCase(), inventoryInfo);
    }

    public PlayerInventoryFeature(String key, PlayerInventoryInfo inventoryInfo) {
        super(key);
        this.inventoryInfo = inventoryInfo;
    }

    @Override
    protected void writeToNBT(NBTTagCompound tag) {
        // nothing else here, the vanilla container code should sort the item stacks out
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        // nothing else here, the vanilla container code should sort the item stacks out
    }

    @Override
    public IGuiPiece getRootPiece(GuiContext context) {
        return new PlayerInventoryGrid(context.getPlayer(), this.inventoryInfo.inventory, this.inventoryInfo.slotsPerRow);
    }
}
