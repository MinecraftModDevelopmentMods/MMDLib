package com.mcmoddev.lib.container.gui;

import com.mcmoddev.lib.container.gui.util.Size2D;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public abstract class BaseSpriteGui extends BaseWidgetGui {
    protected final IGuiSprite sprite;

    protected BaseSpriteGui(final IGuiSprite sprite) {
        this(sprite, sprite.getWidth(), sprite.getHeight());
    }

    protected BaseSpriteGui(final IGuiSprite sprite, final int width, final int height) {
        super(new Size2D(width, height));

        this.sprite = sprite;
    }
}
