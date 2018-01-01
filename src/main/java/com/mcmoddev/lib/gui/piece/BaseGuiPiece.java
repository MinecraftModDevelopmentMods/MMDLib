package com.mcmoddev.lib.gui.piece;

import javax.annotation.Nullable;
import com.mcmoddev.lib.gui.GuiPieceLayer;
import com.mcmoddev.lib.gui.IGuiLayout;
import com.mcmoddev.lib.gui.IGuiPiece;
import com.mcmoddev.lib.gui.IGuiPieceDebugInfo;
import com.mcmoddev.lib.gui.Padding;
import com.mcmoddev.lib.gui.util.Size2D;

@SuppressWarnings("WeakerAccess")
public class BaseGuiPiece implements IGuiPiece, IGuiPieceDebugInfo {
    private final Size2D size;
    private Padding padding = Padding.EMPTY;
    private boolean visible = true;
    private IGuiLayout parentLayout = null;
    private GuiPieceLayer layer = GuiPieceLayer.BACKGROUND;

    protected BaseGuiPiece(int width, int height) {
        this(new Size2D(width, height));
    }

    protected BaseGuiPiece(Size2D size) {
        this.size = size;
    }

    @Override
    public Padding getPadding() {
        return this.padding;
    }

    @Override
    public IGuiPiece setPadding(Padding value) {
        this.padding = value;
        return this;
    }

    @Override
    public Size2D getSize() {
        return this.size;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    @Override
    public boolean setVisibility(boolean isVisible) {
        if (this.canSetVisibility()) {
            this.forceSetVisibility(isVisible);
        }
        return this.visible;
    }

    protected boolean canSetVisibility() {
        return true;
    }

    protected void forceSetVisibility(boolean isVisible) {
        this.visible = isVisible;
    }

    @Nullable
    @Override
    public IGuiLayout getParentLayout() {
        return this.parentLayout;
    }

    @Override
    public IGuiPiece setParentLayout(@Nullable IGuiLayout layout) {
        this.parentLayout = layout;
        return this;
    }

    @Override
    public String getDebugInfo() {
        Size2D size = this.getSize();
        return String.format("w: %d, h: %d", size.width, size.height);
    }

    @Override
    public GuiPieceLayer getLayer() {
        return this.layer;
    }

    protected void setLayer(GuiPieceLayer layer) {
        this.layer = layer;
    }
}
