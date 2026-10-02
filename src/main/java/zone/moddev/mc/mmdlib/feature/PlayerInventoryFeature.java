package zone.moddev.mc.mmdlib.feature;

import java.util.ArrayList;
import java.util.List;
import zone.moddev.mc.mmdlib.container.IWidgetContainer;
import zone.moddev.mc.mmdlib.container.PlayerInventory;
import zone.moddev.mc.mmdlib.container.PlayerInventoryInfo;
import zone.moddev.mc.mmdlib.container.gui.GuiContext;
import zone.moddev.mc.mmdlib.container.gui.IWidgetGui;
import zone.moddev.mc.mmdlib.container.gui.InventoryGrid;
import zone.moddev.mc.mmdlib.container.widget.IWidget;
import zone.moddev.mc.mmdlib.container.widget.PlayerInventoryWidget;
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
        // Vanilla container slots handle inventory synchronization.
    }

    @Override
    public void deserializeNBT(final NBTTagCompound nbt) {
        // Vanilla container slots handle inventory synchronization.
    }

    @SuppressWarnings("serial")
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
