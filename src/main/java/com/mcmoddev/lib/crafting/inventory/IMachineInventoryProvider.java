package com.mcmoddev.lib.crafting.inventory;

import net.minecraft.util.NonNullList;

public interface IMachineInventoryProvider {
    NonNullList<ICraftingInventory> getInventories();
}
