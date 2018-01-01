package com.mcmoddev.lib.gui;

import javax.annotation.Nullable;
import com.mcmoddev.lib.gui.util.Size2D;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public interface IGuiPiece {
    default GuiPieceLayer getLayer() {
        return GuiPieceLayer.BACKGROUND;
    }

    default GuiPieceLayer[] getLayers() {
        return new GuiPieceLayer[] { this.getLayer() };
    }

    default boolean rendersInLayer(GuiPieceLayer layer) {
        for (GuiPieceLayer pieceLayer:this.getLayers()) {
            if (pieceLayer == layer) {
                return true;
            }
        }
        return false;
    }

    Size2D getSize();
    Padding getPadding();
    IGuiPiece setPadding(Padding value);

    default boolean capturesMouseOnClick() { return false; }

    default boolean mouseClicked(MMDGuiContainer container, int mouseX, int mouseY, int mouseButton) { return false; }
    default boolean mouseClickMove(MMDGuiContainer container, int mouseX, int mouseY, int mouseButton) { return false; }
    default boolean mouseReleased(MMDGuiContainer container, int mouseX, int mouseY, int mouseButton) { return false; }

    @SideOnly(Side.CLIENT)
    default void drawBackgroundLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {}
    @SideOnly(Side.CLIENT)
    default void drawMiddleLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {}
    @SideOnly(Side.CLIENT)
    default void drawForegroundLayer(MMDGuiContainer container, int mouseX, int mouseY) {}
    @SideOnly(Side.CLIENT)
    default void drawForegroundTopLayer(MMDGuiContainer container, int mouseX, int mouseY) {}

    default boolean isVisible() { return true; }
    boolean setVisibility(boolean isVisible);

    @Nullable
    IGuiLayout getParentLayout();
    IGuiPiece setParentLayout(@Nullable IGuiLayout layout);

    default Size2D getRenderOffset() {
        int left = 0;
        int top = 0;

        IGuiLayout layout = this.getParentLayout();
        if (layout != null) {
            Size2D offset = layout.getRenderOffset();
            Size2D position = layout.getChildPosition(this);
            left = offset.width + position.width;
            top = offset.height + position.height;
        }

        return new Size2D(left, top);
    }
}
