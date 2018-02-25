package com.mcmoddev.lib.container.gui;

public enum GuiSprites implements IGuiSprite {
    MC_DEMO_BACKGROUND(GuiTextures.MC_DEMO_BACKGROUND, 0, 0, 248, 166),
    MC_SLOT_BACKGROUND(GuiTextures.MC_GENERIC_54, 7, 17, 18, 18),
    MC_BUTTON_DISABLED(GuiTextures.MC_WIDGETS, 0, 46, 200, 20), // TODO: I guess?
    MC_BUTTON(GuiTextures.MC_WIDGETS, 0, 66, 200, 20),
    MC_BUTTON_HOVER(GuiTextures.MC_WIDGETS, 0, 86, 200, 20), // TODO: I guess?

    TANK_CONTAINER(GuiTextures.GUI_BASE, 1, 1, 18, 54),
    TANK_OVERLAY(GuiTextures.GUI_BASE, 3, 56, 14, 50),

    ENERGY_TESLA_EMPTY(GuiTextures.GUI_BASE, 20, 4, 12, 48),
    ENERGY_TESLA_FULL(GuiTextures.GUI_BASE, 33, 4, 12, 48),
    ENERGY_RF_EMPTY(GuiTextures.GUI_BASE, 46, 4, 12, 48),
    ENERGY_RF_FULL(GuiTextures.GUI_BASE, 59, 4, 12, 48),
    ENERGY_GRAY_EMPTY(GuiTextures.GUI_BASE, 72, 4, 12, 48),
    ENERGY_GRAY_FULL(GuiTextures.GUI_BASE, 85, 4, 12, 48)
    ;

    private final IGuiTexture texture;
    private final int left;
    private final int top;
    private final int width;
    private final int height;

    private final boolean needsAlpha;

    GuiSprites(final IGuiTexture texture, final int left, final int top, final int width, final int height) {
        this(texture, left, top, width, height, true);
    }

    GuiSprites(final IGuiTexture texture, final int left, final int top, final int width, final int height, final boolean needsAlpha) {
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
