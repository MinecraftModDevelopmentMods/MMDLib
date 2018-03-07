package com.mcmoddev.lib.container.gui;

import com.mcmoddev.lib.container.gui.util.Size2D;

public class ImageButtonWidgetGui extends BaseButtonWidgetGui {
    private IGuiSprite sprite;

    public ImageButtonWidgetGui(IGuiSprite sprite) {
        this(sprite, sprite.getWidth() + 10, sprite.getHeight() + 10);
    }

    public ImageButtonWidgetGui(IGuiSprite sprite, int width, int height) {
        super(width, height);

        this.sprite = sprite;
    }

    //#region RENDER

    @Override
    public void drawForegroundLayer(MMDGuiContainer container, int mouseX, int mouseY) {
        if (this.sprite == null) {
            return;
        }

        Size2D size = this.getSize();
        this.sprite.draw(container, 0, 0, size.width, size.height, true);
    }

    //#endregion
}
