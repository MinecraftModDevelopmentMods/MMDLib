package zone.moddev.mc.mmdlib.tile;

import zone.moddev.mc.mmdlib.container.gui.FeatureWrapperGui;
import zone.moddev.mc.mmdlib.container.gui.GuiContext;
import zone.moddev.mc.mmdlib.container.gui.IWidgetGui;
import zone.moddev.mc.mmdlib.container.gui.layout.GridLayout;
import zone.moddev.mc.mmdlib.container.gui.layout.SinglePieceWrapper;
import zone.moddev.mc.mmdlib.energy.ForgeEnergyStorage;
import zone.moddev.mc.mmdlib.feature.ForgeEnergyBatteryFeature;

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
