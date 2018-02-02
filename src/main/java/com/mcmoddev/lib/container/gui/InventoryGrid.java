package com.mcmoddev.lib.container.gui;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import com.mcmoddev.lib.container.IContainerSlot;
import com.mcmoddev.lib.container.gui.layout.BaseGridLayout;
import com.mcmoddev.lib.container.gui.util.Size2D;
import com.mcmoddev.lib.util.GuiUtils;
import net.minecraft.inventory.Slot;

public class InventoryGrid extends BaseGridLayout {
    private final int maxColumns;
    private final String[] widgetKeys;
    private ColorOverlayPiece colorOverlay = null;

    public InventoryGrid(int columns, String... widgetKeys) {
        super(columns, 1);

        this.maxColumns = columns;
        this.widgetKeys = widgetKeys;
    }

    @Override
    public void init(GuiContext context) {
        List<IContainerSlot> slots = getContainerSlots(context);

        for(int index = 0; index < slots.size(); index++) {
            int column = index % this.maxColumns;
            int row = index / this.maxColumns;

            this.addPieceInternal(new SpriteBackgroundGui(GuiSprites.MC_SLOT_BACKGROUND, 18, 18), column, row);
        }
    }

    @Override
    public void postInit(GuiContext context) {
        List<IContainerSlot> slots = getContainerSlots(context);

        Size2D offset = context.getGuiContainer().getRenderOffset(this);
        List<Slot> inventorySlots = context.getContainer().inventorySlots;

        int index = 0;
        for(IContainerSlot slot: slots) {
            int column = index % this.maxColumns;
            int row = index / this.maxColumns;
            index++;

            // assume slot is correctly cached
            int slotIndex = slot.getIndex();
            if (slotIndex < inventorySlots.size()) {
                Slot inventorySlot = inventorySlots.get(slotIndex);
                inventorySlot.xPos = offset.width + column * 18 + 1;
                inventorySlot.yPos = offset.height + row * 18 + 1;
            }
        }
    }

    private List<IContainerSlot> getContainerSlots(GuiContext context) {
        return Arrays.stream(this.widgetKeys)
                .map(context::findWidgetByKey)
                .filter(Objects::nonNull)
                .flatMap(widget -> widget.getSlots().stream())
                .collect(Collectors.toList());
    }

    public void setColorOverlay(int overlayColor, int overlayAlpha) {
        this.setColorOverlay(GuiUtils.applyAlpha(overlayColor, overlayAlpha));
    }

    public void setColorOverlay(int overlayColor) {
        GridSizeInfo size = this.getInternalSize();

        if (this.colorOverlay != null) {
            this.colorOverlay.setColor(overlayColor);
        }
        else {
            this.colorOverlay = new ColorOverlayPiece(size.size.width, size.size.height, overlayColor);
            this.addPieceInternal(this.colorOverlay, 0, 0, size.columns, size.rows);
        }
    }
}
