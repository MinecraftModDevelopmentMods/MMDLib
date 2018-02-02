package com.mcmoddev.lib.container.gui;

import com.mcmoddev.lib.container.gui.util.Size2D;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class SpriteForegroundGui extends BaseSpriteGui {
    public SpriteForegroundGui(IGuiSprite sprite, int width, int height) {
        super(sprite, width, height);
    }

    public SpriteForegroundGui(IGuiSprite sprite) {
        super(sprite);
    }

    @Override
    public GuiPieceLayer getLayer() {
        return GuiPieceLayer.FOREGROUND;
    }

    @Override
    public void drawForegroundLayer(MMDGuiContainer container, int mouseX, int mouseY) {
        Size2D size = this.getSize();
        this.sprite.draw(container, 0, 0, size.width, size.height, true);
    }
}
