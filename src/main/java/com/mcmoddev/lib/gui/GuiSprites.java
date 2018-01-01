package com.mcmoddev.lib.gui;

public enum GuiSprites implements IGuiSprite {
    MC_DEMO_BACKGROUND(GuiTextures.MC_DEMO_BACKGROUND, 0, 0, 248, 166),
    MC_SLOT_BACKGROUND(GuiTextures.MC_GENERIC_54, 7, 17, 18, 18),

    TANK_CONTAINER(GuiTextures.GUI_BASE, 1, 1, 18, 54),
    TANK_OVERLAY(GuiTextures.GUI_BASE, 3, 56, 14, 50)
    ;

    private final IGuiTexture texture;
    private final int left;
    private final int top;
    private final int width;
    private final int height;

    private final boolean needsAlpha;

    GuiSprites(IGuiTexture texture, int left, int top, int width, int height) {
        this(texture, left, top, width, height, true);
    }

    GuiSprites(IGuiTexture texture, int left, int top, int width, int height, boolean needsAlpha) {
        this.texture = texture;
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.needsAlpha = needsAlpha;
    }

    @Override
    public IGuiTexture getTexture() {
        return this.texture;
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
    public boolean needsAlpha() {
        return this.needsAlpha;
    }
}
