package com.mcmoddev.lib.container.gui;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Provides a way to generically reference a 2D Sprite.
 */
public interface IGuiSprite {
    /**
     * Returns the resource location for this texture.
     * @return The resource location for this texture.
     */
    IGuiTexture getTexture();

    /**
     * Gets the x coordinate of this sprite within the texture.
     * @return The x coordinate of this sprite within the texture.
     */
    int getLeft();

    /**
     * Gets the y coordinate of this sprite within the texture.
     * @return The y coordinate of this sprite within the texture.
     */
    int getTop();

    /**
     * Gets the width of this sprite within the texture.
     * @return The width of this sprite within the texture.
     */
    int getWidth();

    /**
     * Gets the height of this sprite within the texture.
     * @return The height of this sprite within the texture.
     */
    int getHeight();

    /**
     * Returns a flag specifying if this sprite requires an alpha channel when rendering.
     * @return True if this sprite requires alpha when rendering. False otherwise.
     */
    boolean needsAlpha();

    /**
     * Draw this sprite onto a screen. Uses sprite's own width and height to compute the render area.
     * @param screen Screen to render onto.
     * @param left X coordinate on the screen where to render the sprite.
     * @param top Y coordinate on the screen where to render the sprite.
     */
    @SideOnly(Side.CLIENT)
    default void draw(final GuiScreen screen, final int left, final int top) {
        this.draw(screen, left, top, this.getWidth(), this.getHeight(), false);
    }

    /**
     * Draw this sprite onto a screen. The sprite will be centered inside the target area. If target area is smaller
     * than the sprite, clip will occur.
     * @param screen Screen to render onto.
     * @param left X coordinate on the screen where to render the sprite.
     * @param top Y coordinate on the screen where to render the sprite.
     * @param width Width of the target area.
     * @param height Height of the target area.
     */
    @SideOnly(Side.CLIENT)
    default void draw(final GuiScreen screen, final int left, final int top, final int width, final int height) {
        this.draw(screen, left, top, width, height, true);
    }

    /**
     * Draw this sprite onto a screen. The sprite will be centered inside the target area.
     * @param screen Screen to render onto.
     * @param left X coordinate on the screen where to render the sprite.
     * @param top Y coordinate on the screen where to render the sprite.
     * @param width Width of the target area.
     * @param height Height of the target area.
     * @param clip Specifies the before when the tile is bigger than the target area. True for sprite to get clipped.
     *             False if it can render outside the target area.
     */
    @SideOnly(Side.CLIENT)
    default void draw(final GuiScreen screen, final int left, final int top, final int width, final int height, final boolean clip) {
        int texLeft = this.getLeft();
        int texTop = this.getTop();
        int texWidth = this.getWidth();
        int texHeight = this.getHeight();

        final int fullLeft = left + (width - texWidth) / 2;
        final int fullTop = top + (height - texHeight) / 2;

        if (clip) {
            if (texWidth > width) {
                texLeft += (texWidth - width) / 2;
                texWidth = width;
            }
            if (texHeight > height) {
                texTop += (texHeight - height) / 2;
                texHeight = height;
            }
        }

        this.getTexture().bind();

        if (this.needsAlpha()) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }

        screen.drawTexturedModalRect(fullLeft, fullTop, texLeft, texTop, texWidth, texHeight);

        if (this.needsAlpha()) {
            GlStateManager.disableBlend();
        }
    }

    /**
     * Draws parts of this sprite onto a screen.
     * @param screen Screen to render onto.
     * @param left X coordinate on the screen where to render the sprite.
     * @param top Y coordinate on the screen where to render the sprite.
     * @param texLeft X coordinate of the sprite part that will be rendered.
     * @param texTop Y coordinate of the sprite part that will be rendered.
     * @param texWidth Width of the sprite part that will be rendered.
     * @param texHeight Height of the sprite part that will be rendered.
     */
    @SideOnly(Side.CLIENT)
    default void draw(final GuiScreen screen, final int left, final int top, final int texLeft, final int texTop, final int texWidth, final int texHeight) {
        this.getTexture().bind();

        if (this.needsAlpha()) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }

        screen.drawTexturedModalRect(left, top,
            this.getLeft() + texLeft,
            this.getTop() + texTop,
            texWidth,
            texHeight);

        if (this.needsAlpha()) {
            GlStateManager.disableBlend();
        }
    }
}
