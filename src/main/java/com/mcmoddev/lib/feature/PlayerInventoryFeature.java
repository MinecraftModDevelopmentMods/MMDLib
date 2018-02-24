package com.mcmoddev.lib.feature;

import java.util.ArrayList;
import java.util.List;
import com.mcmoddev.lib.container.IWidgetContainer;
import com.mcmoddev.lib.container.PlayerInventory;
import com.mcmoddev.lib.container.PlayerInventoryInfo;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.InventoryGrid;
import com.mcmoddev.lib.container.widget.IWidget;
import com.mcmoddev.lib.container.widget.PlayerInventoryWidget;
import net.minecraft.nbt.NBTTagCompound;

public class PlayerInventoryFeature extends BaseFeature implements IWidgetContainer {
    private final PlayerInventoryInfo inventoryInfo;

    public PlayerInventoryFeature(final PlayerInventory inventory, final int slotsPerRow) {
        this(new PlayerInventoryInfo(inventory, slotsPerRow));
    }

    public PlayerInventoryFeature(final String key, final PlayerInventory inventory, final int slotsPerRow) {
        this(key, new PlayerInventoryInfo(inventory, slotsPerRow));
    }

    public PlayerInventoryFeature(final PlayerInventoryInfo inventoryInfo) {
        this("player_" + inventoryInfo.inventory.name().toLowerCase(), inventoryInfo);
    }

    public PlayerInventoryFeature(final String key, final PlayerInventoryInfo inventoryInfo) {
        super(key);
        this.inventoryInfo = inventoryInfo;
    }

    @Override
    protected void writeToNBT(final NBTTagCompound tag) {
        // nothing else here, the vanilla container code should sort the item stacks out
    }

    @Override
    public void deserializeNBT(final NBTTagCompound nbt) {
        // nothing else here, the vanilla container code should sort the item stacks out
    }

    @Override
    public List<IWidget> getWidgets(final GuiContext context) {
        return new ArrayList<IWidget>() {{
            add(new PlayerInventoryWidget(
                PlayerInventoryFeature.this.getKey() + "_slots",
                context.getPlayer(),
                PlayerInventoryFeature.this.inventoryInfo.inventory));
        }};
    }

    @Override
    public IWidgetGui getRootWidgetGui(final GuiContext context) {
        return new InventoryGrid(this.inventoryInfo.slotsPerRow, this.getKey() + "_slots");
    }
}
