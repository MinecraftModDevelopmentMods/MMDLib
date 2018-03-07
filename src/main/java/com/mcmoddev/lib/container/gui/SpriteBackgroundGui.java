package com.mcmoddev.lib.container.gui;

import com.mcmoddev.lib.container.gui.util.Size2D;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class SpriteBackgroundGui extends BaseSpriteGui {
    public SpriteBackgroundGui(final IGuiSprite sprite, final int width, final int height) {
        super(sprite, width, height);
    }

    public SpriteBackgroundGui(final IGuiSprite sprite) {
        super(sprite);
    }

    @Override
    public GuiPieceLayer getLayer() {
        return GuiPieceLayer.BACKGROUND;
    }

    @Override
    public void drawBackgroundLayer(final MMDGuiContainer container, final float partialTicks, final int mouseX, final int mouseY) {
        final Size2D size = this.getSize();
        this.sprite.draw(container, 0, 0, size.width, size.height, true);
    }
}
