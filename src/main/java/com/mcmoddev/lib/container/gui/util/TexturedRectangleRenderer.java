package com.mcmoddev.lib.container.gui.util;

import com.mcmoddev.lib.container.gui.IGuiSprite;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Helper class used to "stretch" a sprite over a specified area while keeping a non-stretched margin.
 */
@SuppressWarnings("WeakerAccess")
public final class TexturedRectangleRenderer {
    private TexturedRectangleRenderer() {}

    /**
     * Renders a sprite "stretched" over a specified area while keeping a non-stretched margin.
     * @param screen The gui screen to draw on.
     * @param sprite The sprite to render.
     * @param borderSize The size of the fixed margin.
     * @param width The width of the final area.
     * @param height The height of the final area.
     */
    @SideOnly(Side.CLIENT)
    public static void drawOnGUI(final GuiScreen screen, final IGuiSprite sprite, final int borderSize, final int width, final int height) {
        drawOnGUI(screen, sprite, borderSize, 1, width, height);
    }

    /**
     * Renders a sprite "stretched" over a specified area while keeping a non-stretched margin.
     * @param screen The gui screen to draw on.
     * @param sprite The sprite to render.
     * @param borderSize The size of the fixed margin.
     * @param overlap The size to overlap sub-rectangles when rendering.
     * @param width The width of the final area.
     * @param height The height of the final area.
     */
    @SideOnly(Side.CLIENT)
    public static void drawOnGUI(final GuiScreen screen, final IGuiSprite sprite, final int borderSize, final int overlap, final int width, final int height) {
        drawOnGUI(screen, sprite, borderSize, overlap, 0, 0, width, height);
    }

    /**
     * Renders a sprite "stretched" over a specified area while keeping a non-stretched margin.
     * @param screen The gui screen to draw on.
     * @param sprite The sprite to render.
     * @param borderSize The size of the fixed margin.
     * @param overlap The size to overlap sub-rectangles when rendering.
     * @param left The x coordinate to draw at.
     * @param top The y coordinate to draw at.
     * @param width The width of the final area.
     * @param height The height of the final area.
     */
    @SideOnly(Side.CLIENT)
    public static void drawOnGUI(final GuiScreen screen, final IGuiSprite sprite, final int borderSize, final int overlap, final int left, final int top, final int width, final int height) {
        // TODO: optimize/cache the following computations
        final int innerWidth = sprite.getWidth() - borderSize * 2;
        final int innerHeight = sprite.getHeight() - borderSize * 2;

        final int innerRows = (int) Math.ceil((double) (height - (borderSize * 2)) / (double) innerHeight);
        final int innerColumns = (int) Math.ceil((double) (width - (borderSize * 2)) / (double) innerWidth);

        sprite.getTexture().bind();
        for (int r = 0; r < innerRows; r++) {
            final int renderTop = borderSize + (r * innerHeight);
            final int renderBottom = Math.min(renderTop + innerHeight, height - borderSize);

            if (renderBottom > renderTop) {
                for (int c = 0; c < innerColumns; c++) {
                    final int renderLeft = borderSize + (c * innerWidth);
                    final int renderRight = Math.min(renderLeft + innerWidth, width - borderSize);

                    if (renderRight > renderLeft) {
                        screen.drawTexturedModalRect(left + renderLeft, top + renderTop, sprite.getLeft() + borderSize, sprite.getTop() + borderSize, renderRight - renderLeft, renderBottom - renderTop);
                    }
                }

                screen.drawTexturedModalRect(left, top + renderTop, sprite.getLeft(), sprite.getTop() + borderSize, borderSize + overlap, renderBottom - renderTop);
                screen.drawTexturedModalRect(left + width - borderSize - overlap, top + renderTop, sprite.getLeft() + sprite.getWidth() - borderSize - overlap, sprite.getTop() + borderSize, borderSize + overlap, renderBottom - renderTop);
            }
        }
        for (int c = 0; c < innerColumns; c++) {
            final int renderLeft = borderSize + (c * innerWidth);
            final int renderRight = Math.min(renderLeft + innerWidth, width - borderSize);

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
