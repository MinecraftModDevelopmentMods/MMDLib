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

    public InventoryGrid(final int columns, final String... widgetKeys) {
        super(columns, 1);

        this.maxColumns = columns;
        this.widgetKeys = widgetKeys;
    }

    @Override
    public void init(final GuiContext context) {
        final List<IContainerSlot> slots = this.getContainerSlots(context);

        for(int index = 0; index < slots.size(); index++) {
            final int column = index % this.maxColumns;
            final int row = index / this.maxColumns;

            this.addPieceInternal(new SpriteBackgroundGui(GuiSprites.MC_SLOT_BACKGROUND, 18, 18), column, row);
        }

        if (this.colorOverlay != null) {
            final GridSizeInfo size = this.getInternalSize();
            this.colorOverlay.setSize(size.size);
            this.updatePieceInternal(this.colorOverlay, 0, 0, size.columns, size.rows);
        }
    }

    @Override
    public void postInit(final GuiContext context) {
        final List<IContainerSlot> slots = this.getContainerSlots(context);

        final Size2D offset = context.getGuiContainer().getRenderOffset(this);
        final List<Slot> inventorySlots = context.getContainer().inventorySlots;

        int index = 0;
        for(final IContainerSlot slot: slots) {
            final int column = index % this.maxColumns;
            final int row = index / this.maxColumns;
            index++;

            // assume slot is correctly cached
            final int slotIndex = slot.getIndex();
            if (slotIndex < inventorySlots.size()) {
                final Slot inventorySlot = inventorySlots.get(slotIndex);
                inventorySlot.xPos = offset.width + column * 18 + 1;
                inventorySlot.yPos = offset.height + row * 18 + 1;
            }
        }
    }

    private List<IContainerSlot> getContainerSlots(final GuiContext context) {
        return Arrays.stream(this.widgetKeys)
                .map(context::findWidgetByKey)
                .filter(Objects::nonNull)
                .flatMap(widget -> widget.getSlots().stream())
                .collect(Collectors.toList());
    }

    public void setColorOverlay(final int overlayColor, final int overlayAlpha) {
        this.setColorOverlay(GuiUtils.applyAlpha(overlayColor, overlayAlpha));
    }

    public void setColorOverlay(final int overlayColor) {
        final GridSizeInfo size = this.getInternalSize();

        if (this.colorOverlay != null) {
            this.colorOverlay.setColor(overlayColor);
        }
        else {
            this.colorOverlay = new ColorOverlayPiece(size.size.width, size.size.height, overlayColor);
            this.addPieceInternal(this.colorOverlay, 0, 0, size.columns, size.rows);
        }
    }
}
