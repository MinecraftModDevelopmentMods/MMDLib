package com.mcmoddev.lib.tile;

import com.mcmoddev.lib.container.PlayerInventory;
import com.mcmoddev.lib.container.gui.FeatureWrapperGui;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.layout.SinglePieceWrapper;
import com.mcmoddev.lib.container.gui.layout.VerticalStackLayout;
import com.mcmoddev.lib.container.gui.util.Padding;
import com.mcmoddev.lib.feature.PlayerInventoryFeature;

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
