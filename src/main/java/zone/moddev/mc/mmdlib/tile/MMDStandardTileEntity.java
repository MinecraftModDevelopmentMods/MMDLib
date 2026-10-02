package zone.moddev.mc.mmdlib.tile;

import zone.moddev.mc.mmdlib.container.PlayerInventory;
import zone.moddev.mc.mmdlib.container.gui.FeatureWrapperGui;
import zone.moddev.mc.mmdlib.container.gui.GuiContext;
import zone.moddev.mc.mmdlib.container.gui.IWidgetGui;
import zone.moddev.mc.mmdlib.container.gui.layout.SinglePieceWrapper;
import zone.moddev.mc.mmdlib.container.gui.layout.VerticalStackLayout;
import zone.moddev.mc.mmdlib.container.gui.util.Padding;
import zone.moddev.mc.mmdlib.feature.PlayerInventoryFeature;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class MMDStandardTileEntity extends MMDFeaturesTileEntity {
    protected MMDStandardTileEntity() {
        super();

        this.addFeature(new PlayerInventoryFeature(PlayerInventory.INVENTORY, 9));
        this.addFeature(new PlayerInventoryFeature(PlayerInventory.QUICKBAR, 9));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IWidgetGui getRootWidgetGui(final GuiContext context) {
        return new VerticalStackLayout()
            .addPiece(new SinglePieceWrapper(this.getMainContentWidgetGui(context)))
            .addPiece(new FeatureWrapperGui(context, this, "player_inventory")
                .setPadding(Padding.top(7))
            )
            .addPiece(new FeatureWrapperGui(context, this, "player_quickbar")
                .setPadding(Padding.top(7))
            );
    }

    @SideOnly(Side.CLIENT)
    protected abstract IWidgetGui getMainContentWidgetGui(GuiContext context);
}
