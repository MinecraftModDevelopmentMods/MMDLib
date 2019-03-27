package com.mcmoddev.lib.container.gui;

import javax.annotation.Nullable;
import com.mcmoddev.lib.container.gui.util.Padding;
import com.mcmoddev.lib.container.gui.util.Size2D;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public abstract class BaseWidgetGui implements IWidgetGui {
    private Padding padding = Padding.EMPTY;
    private boolean isVisible = true;
    private IWidgetLayout parent = null;

    private Size2D size = null;

    protected BaseWidgetGui() {}

    protected BaseWidgetGui(final int width, final int height) {
        this(new Size2D(width, height));
    }

    protected BaseWidgetGui(final Size2D size) {
        this.size = size;
    }

    //#region PADDING

    @Override
    public Padding getPadding() {
        return this.padding;
    }

    @Override
    public IWidgetGui setPadding(final Padding value) {
        this.padding = value;
        return this;
    }

    //#endregion

    //#region VISIBILITY

    @Override
    public boolean isVisible() {
        return this.isVisible;
    }

    @Override
    public boolean setVisibility(final boolean isVisible) {
        if (this.canSetVisibility()) {
            this.setVisibilityProtected(isVisible);
        }
        return this.isVisible();
    }

    protected void setVisibilityProtected(final boolean isVisible) {
        this.isVisible = isVisible;
    }

    protected boolean canSetVisibility() {
        return true;
    }

    //#endregion

    //#region PARENT LAYOUT

    @Nullable
    @Override
    public IWidgetLayout getParentLayout() {
        return this.parent;
    }

    @Override
    public IWidgetGui setParentLayout(@Nullable final IWidgetLayout layout) {
        this.parent = layout;
        return this;
    }

    //#endregion

    //#region SIZE

    @Override
    public Size2D getSize() {
        if (this.size == null) {
            // I would throw an error here but java error handling sucks balls
            return Size2D.ZERO;
        }

        return this.size;
    }

    public void setSize(final Size2D value) {
        this.size = value;
    }

    //#endregion

    protected boolean isInside(final int x, final int y) {
        final Size2D size = this.getSize();
        return ((x >= 0) && (y >= 0) && (x <= size.width) && (y <= size.height));
    }
}
