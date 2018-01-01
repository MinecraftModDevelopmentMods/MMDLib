package com.mcmoddev.lib.gui.piece;

import com.mcmoddev.lib.gui.GuiPieceLayer;
import com.mcmoddev.lib.gui.IGuiSprite;
import com.mcmoddev.lib.gui.MMDGuiContainer;
import com.mcmoddev.lib.gui.util.Size2D;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class GuiForegroundSpritePiece extends BaseSpriteGuiPiece {
    public GuiForegroundSpritePiece(IGuiSprite sprite, int width, int height) {
        super(sprite, width, height);
    }

    public GuiForegroundSpritePiece(IGuiSprite sprite) {
        super(sprite);
    }

    @Override
    public GuiPieceLayer getLayer() {
        return GuiPieceLayer.FOREGROUND;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawForegroundLayer(MMDGuiContainer container, int mouseX, int mouseY) {
        Size2D size = this.getSize();
        this.sprite.draw(container, 0, 0, size.width, size.height, true);
    }
}
