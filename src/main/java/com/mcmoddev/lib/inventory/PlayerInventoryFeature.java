package com.mcmoddev.lib.inventory;

import java.util.List;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.container.IPlayerInventoryProvider;
import com.mcmoddev.lib.container.MMDContainer;
import com.mcmoddev.lib.container.PlayerInventory;
import com.mcmoddev.lib.container.PlayerInventoryInfo;
import com.mcmoddev.lib.feature.BaseFeature;
import net.minecraft.nbt.NBTTagCompound;

public class PlayerInventoryFeature extends BaseFeature implements IPlayerInventoryProvider {
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
    public List<PlayerInventoryInfo> getPlayerSlots(MMDContainer container) {
        return Lists.newArrayList(this.inventoryInfo);
    }

    @Override
    protected void writeToNBT(NBTTagCompound tag) {
        // nothing else here, the vanilla container code should sort the item stacks out
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        // nothing else here, the vanilla container code should sort the item stacks out
    }
}
