package com.mcmoddev.lib.gui.piece;

import com.mcmoddev.lib.gui.IGuiSprite;
import com.mcmoddev.lib.gui.MMDGuiContainer;
import com.mcmoddev.lib.gui.util.Size2D;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class GuiBackgroundSpritePiece extends BaseSpriteGuiPiece {
    public GuiBackgroundSpritePiece(IGuiSprite sprite, int width, int height) {
        super(sprite, width, height);
    }

    public GuiBackgroundSpritePiece(IGuiSprite sprite) {
        super(sprite);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawBackgroundLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {
        Size2D size = this.getSize();
        this.sprite.draw(container, 0, 0, size.width, size.height, true);
    }
}
