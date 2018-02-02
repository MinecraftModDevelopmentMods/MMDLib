package com.mcmoddev.lib.container.gui;

import com.mcmoddev.lib.container.gui.util.Size2D;
import com.mcmoddev.lib.util.GuiUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings("WeakerAccess")
public class ColorOverlayPiece extends BaseWidgetGui {
    private int color;

    public ColorOverlayPiece(int width, int height, int color, int alpha) {
        super(width, height);
        this.setColor(color, alpha);
    }

    public ColorOverlayPiece(int width, int height, int color) {
        super(width, height);
        this.setColor(color);
    }

    public int getColor() {
        return this.color;
    }

    public void setColor(int color, int alpha) {
        this.setColor(GuiUtils.applyAlpha(color, alpha));
    }

    public void setColor(int color) {
        this.color = color;
    }

    @Override
    public GuiPieceLayer getLayer() {
        return GuiPieceLayer.MIDDLE;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawMiddleLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {
        Size2D size = this.getSize();
        container.drawFilledRect(0, 0, size.width, size.height, this.color);
    }
}
