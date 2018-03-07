package com.mcmoddev.lib.tile;

import com.mcmoddev.lib.container.gui.FeatureWrapperGui;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.layout.GridLayout;
import com.mcmoddev.lib.container.gui.layout.SinglePieceWrapper;
import com.mcmoddev.lib.energy.ForgeEnergyStorage;
import com.mcmoddev.lib.feature.ForgeEnergyBatteryFeature;

@SuppressWarnings("WeakerAccess")
public abstract class MMDEnergyConsumerTileEntity extends MMDStandardTileEntity {
    public static final int DEFAULT_ENERGY_CAPACITY = 50000;
    public static final int DEFAULT_ENERGY_INPUT_RATE = 120;
    protected final ForgeEnergyStorage battery;

    protected MMDEnergyConsumerTileEntity() {
        this(DEFAULT_ENERGY_CAPACITY);
    }

    protected MMDEnergyConsumerTileEntity(final int capacity) {
        this(capacity, DEFAULT_ENERGY_INPUT_RATE);
    }

    protected MMDEnergyConsumerTileEntity(final int capacity, final int maxInputRate) {
        super();

        this.battery = this.addFeature(new ForgeEnergyBatteryFeature("battery",
            0, capacity, maxInputRate, 0))
            .getEnergyStorage();
    }

    @Override
    protected final IWidgetGui getMainContentWidgetGui(final GuiContext context) {
        return new GridLayout(9, 1)
            .addPiece(new FeatureWrapperGui(context, this, "battery"), 0, 0, 1, 1)
            .addPiece(new SinglePieceWrapper(this.getContentWidgetGui(context)), 1, 0, 8, 1);
    }

    protected abstract IWidgetGui getContentWidgetGui(GuiContext context);
}
