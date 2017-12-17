package com.mcmoddev.lib.gui.piece;

import com.mcmoddev.lib.gui.IGuiPiece;

public class BaseGuiPiece implements IGuiPiece {
    private final int left, top, width, height;
    private boolean visible = true;

    protected BaseGuiPiece(int left, int top, int width, int height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
    }

    @Override
    public int getLeft() {
        return this.left;
    }

    @Override
    public int getTop() {
        return this.top;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
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
}
