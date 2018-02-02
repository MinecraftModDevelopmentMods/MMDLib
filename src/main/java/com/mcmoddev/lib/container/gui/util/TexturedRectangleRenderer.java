package com.mcmoddev.lib.container.gui.util;

import com.mcmoddev.lib.container.gui.IGuiSprite;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public final class TexturedRectangleRenderer {
    private TexturedRectangleRenderer() {}

    @SideOnly(Side.CLIENT)
    public static void drawOnGUI(GuiScreen screen, IGuiSprite sprite, int cornerSize, int width, int height) {
        drawOnGUI(screen, sprite, cornerSize, 1, width, height);
    }

    @SideOnly(Side.CLIENT)
    public static void drawOnGUI(GuiScreen screen, IGuiSprite sprite, int cornerSize, int overlap, int width, int height) {
        drawOnGUI(screen, sprite, cornerSize, overlap, 0, 0, width, height);
    }

    @SideOnly(Side.CLIENT)
    public static void drawOnGUI(GuiScreen screen, IGuiSprite sprite, int borderSize, int overlap, int left, int top, int width, int height) {
        // TODO: optimize/cache the following computations
        int innerWidth = sprite.getWidth() - borderSize * 2;
        int innerHeight = sprite.getHeight() - borderSize * 2;

        int innerRows = (int) Math.ceil((double) (height - (borderSize * 2)) / (double) innerHeight);
        int innerColumns = (int) Math.ceil((double) (width - (borderSize * 2)) / (double) innerWidth);

        sprite.getTexture().bind();
        for (int r = 0; r < innerRows; r++) {
            int renderTop = borderSize + (r * innerHeight);
            int renderBottom = Math.min(renderTop + innerHeight, height - borderSize);

            if (renderBottom > renderTop) {
                for (int c = 0; c < innerColumns; c++) {
                    int renderLeft = borderSize + (c * innerWidth);
                    int renderRight = Math.min(renderLeft + innerWidth, width - borderSize);

                    if (renderRight > renderLeft) {
                        screen.drawTexturedModalRect(left + renderLeft, top + renderTop, sprite.getLeft() + borderSize, sprite.getTop() + borderSize, renderRight - renderLeft, renderBottom - renderTop);
                    }
                }

                screen.drawTexturedModalRect(left, top + renderTop, sprite.getLeft(), sprite.getTop() + borderSize, borderSize + overlap, renderBottom - renderTop);
                screen.drawTexturedModalRect(left + width - borderSize - overlap, top + renderTop, sprite.getLeft() + sprite.getWidth() - borderSize - overlap, sprite.getTop() + borderSize, borderSize + overlap, renderBottom - renderTop);
            }
        }
        for (int c = 0; c < innerColumns; c++) {
            int renderLeft = borderSize + (c * innerWidth);
            int renderRight = Math.min(renderLeft + innerWidth, width - borderSize);

            if (renderRight > renderLeft) {
                screen.drawTexturedModalRect(left + renderLeft, top, sprite.getLeft() + borderSize, sprite.getTop(), renderRight - renderLeft, borderSize + overlap);
                screen.drawTexturedModalRect(left + renderLeft, top + height - borderSize - overlap, sprite.getLeft() + borderSize, sprite.getTop() + sprite.getHeight() - borderSize - overlap, renderRight - renderLeft, borderSize + overlap);
            }
        }

        screen.drawTexturedModalRect(left, top, sprite.getLeft(), sprite.getTop(), borderSize + overlap, borderSize + overlap);
        screen.drawTexturedModalRect(left, top + height - borderSize - overlap, sprite.getLeft(), sprite.getTop() +sprite.getHeight() - borderSize - overlap, borderSize + overlap, borderSize + overlap);
        screen.drawTexturedModalRect(left + width - borderSize - overlap, top + height - borderSize - overlap, sprite.getLeft() + sprite.getWidth() - borderSize - overlap, sprite.getTop() + sprite.getHeight() - borderSize - overlap, borderSize + overlap, borderSize + overlap);
        screen.drawTexturedModalRect(left + width - borderSize - overlap, top, sprite.getLeft() + sprite.getWidth() - borderSize - overlap, sprite.getTop(), borderSize + overlap, borderSize + overlap);
    }
}
