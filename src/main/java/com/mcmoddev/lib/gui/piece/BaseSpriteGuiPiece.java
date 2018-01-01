package com.mcmoddev.lib.gui.piece;

import com.mcmoddev.lib.gui.IGuiSprite;

@SuppressWarnings("WeakerAccess")
public class BaseSpriteGuiPiece extends BaseGuiPiece {
    protected final IGuiSprite sprite;

    protected BaseSpriteGuiPiece(IGuiSprite sprite) {
        this(sprite, sprite.getWidth(), sprite.getHeight());
    }

    protected BaseSpriteGuiPiece(IGuiSprite sprite, int width, int height) {
        super(width, height);

        this.sprite = sprite;
    }
}
