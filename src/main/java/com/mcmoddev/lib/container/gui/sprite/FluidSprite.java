package com.mcmoddev.lib.container.gui.sprite;

import java.awt.Color;
import com.mcmoddev.lib.container.gui.IGuiSprite;
import com.mcmoddev.lib.container.gui.IGuiTexture;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * {@link IGuiSprite} implementation used to render a fluid.
 */
public class FluidSprite implements IGuiSprite {
    private final Fluid fluid;
    private final int width;
    private final int height;

    /**
     * Initializes a new instance of FluidSprite.
     * @param fluid The fluid to render.
     * @param width The width of the sprite.
     * @param height The height of the sprite.
     */
    public FluidSprite(final Fluid fluid, final int width, final int height) {
        this.fluid = fluid;
        this.width = width;
        this.height = height;
    }

    @Override
    public IGuiTexture getTexture() {
        return MinecraftStitchedTextures.INSTANCE;
    }

    @Override
    public int getLeft() { return 0; }

    @Override
    public int getTop() { return 0; }

    @Override
    public int getWidth() { return this.width; }

    @Override
    public int getHeight() { return this.height; }

    @Override
    public boolean needsAlpha() {
        // TODO: test fluid
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(final GuiScreen screen, final int left, final int top) {
        this.draw(screen, left, top, this.getWidth(), this.getHeight());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(final GuiScreen screen, final int left, final int top, final int width, final int height, final boolean clip) {
        this.draw(screen, left, top, this.getWidth(), this.getHeight());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(final GuiScreen screen, final int left, final int top, final int width, final int height) {
        this.draw(screen, left, top, 0, 0, width, height);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(final GuiScreen screen, final int left, final int top, final int texLeft, final int texTop, final int texWidth, final int texHeight) {
        final ResourceLocation still = this.fluid.getStill();
        final TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(still.toString());

        final int spriteWidth = sprite.getIconWidth();
        final int spriteHeight = sprite.getIconHeight();

        this.getTexture().bind();
        final Color color = new Color(this.fluid.getColor(), true);
        GlStateManager.color(
            (float)color.getRed() / 255f,
            (float)color.getGreen() / 255f,
            (float)color.getBlue() / 255f,
            (float)color.getAlpha() / 255f);

        final Tessellator tessellator = Tessellator.getInstance();
        final BufferBuilder bufferbuilder = tessellator.getBuffer();
        bufferbuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        final double u = sprite.getInterpolatedU(0.0);
        final double v = sprite.getInterpolatedV(0.0);

        int tileY = texTop;
        while (tileY < texHeight) {
            int tileX = texLeft;
            while (tileX < texWidth) {
                final double x = left + tileX;
                final double y = top + tileY;
                final double w = Math.min(tileX + spriteWidth, this.width) - tileX;
                final double h = Math.min(tileY + spriteHeight, this.height) - tileY;
                final double ur = w / spriteWidth;
                final double vb = h / spriteHeight;

                final double uu = sprite.getInterpolatedU(ur * 16.0);
                final double vv = sprite.getInterpolatedV(vb * 16.0);

                bufferbuilder.pos(x, y + h, 0.0D).tex(u, v).endVertex();
                bufferbuilder.pos(x + w, y + h, 0.0D).tex(uu, v).endVertex();
                bufferbuilder.pos(x + w, y, 0.0D).tex(uu, vv).endVertex();
                bufferbuilder.pos(x, y, 0.0D).tex(u, vv).endVertex();
                tileX += spriteWidth;
            }
            tileY += spriteHeight;
        }

        tessellator.draw();
    }
}
