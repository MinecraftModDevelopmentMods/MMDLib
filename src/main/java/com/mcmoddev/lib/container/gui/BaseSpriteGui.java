package com.mcmoddev.lib.container.gui;

import com.mcmoddev.lib.container.gui.util.Size2D;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings("WeakerAccess")
@SideOnly(Side.CLIENT)
public abstract class BaseSpriteGui extends BaseWidgetGui {
    protected final IGuiSprite sprite;

    protected BaseSpriteGui(IGuiSprite sprite) {
        this(sprite, sprite.getWidth(), sprite.getHeight());
    }

    protected BaseSpriteGui(IGuiSprite sprite, int width, int height) {
        super(new Size2D(width, height));

        this.sprite = sprite;
    }
}
