package com.mcmoddev.lib.gui;

public enum GuiSprites implements IGuiSprite {
    MC_DEMO_BACKGROUND(GuiTextures.MC_DEMO_BACKGROUND, 0, 0, 248, 166),
    MC_SLOT_BACKGROUND(GuiTextures.MC_GENERIC_54, 7, 17, 18, 18)
    ;

    private final IGuiTexture texture;
    private final int left;
    private final int top;
    private final int width;
    private final int height;

    GuiSprites(IGuiTexture texture, int left, int top, int width, int height) {
        this.texture = texture;
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
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
}
