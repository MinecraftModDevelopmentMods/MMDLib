package com.mcmoddev.lib.gui.piece;

import java.awt.Color;
import com.mcmoddev.lib.gui.MMDGuiContainer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ColorOverlayPiece extends BaseGuiPiece {
    private int color;

    public ColorOverlayPiece(int left, int top, int width, int height, int color, int alpha) {
        super(left, top, width, height);
        this.setColor(color, alpha);
    }

    public ColorOverlayPiece(int left, int top, int width, int height, int color) {
        super(left, top, width, height);
        this.setColor(color);
    }

    public int getColor() {
        return this.color;
    }

    public void setColor(int color, int alpha) {
        Color base = new Color(color);
        this.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha).getRGB());
    }

    public void setColor(int color) {
        this.color = color;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawMiddleLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {
        int left = container.getRenderLeft() + this.getLeft();
        int top = container.getRenderTop() + this.getTop();

        container.drawFilledRect(left, top, this.getWidth(), this.getHeight(), this.color);
    }
}
