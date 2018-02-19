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

public class FluidSprite implements IGuiSprite {
    private final Fluid fluid;
    private final int width;
    private final int height;

    public FluidSprite(Fluid fluid, int width, int height) {
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
    public void draw(GuiScreen screen, int left, int top) {
        this.draw(screen, left, top, this.getWidth(), this.getHeight());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(GuiScreen screen, int left, int top, int width, int height, boolean clip) {
        this.draw(screen, left, top, this.getWidth(), this.getHeight());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(GuiScreen screen, int left, int top, int width, int height) {
        this.draw(screen, left, top, 0, 0, width, height);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(GuiScreen screen, int left, int top, int texLeft, int texTop, int texWidth, int texHeight) {
        ResourceLocation still = this.fluid.getStill();
        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(still.toString());

        int spriteWidth = sprite.getIconWidth();
        int spriteHeight = sprite.getIconHeight();

        this.getTexture().bind();
        Color color = new Color(this.fluid.getColor(), true);
        GlStateManager.color(
            (float)color.getRed() / 255f,
            (float)color.getGreen() / 255f,
            (float)color.getBlue() / 255f,
            (float)color.getAlpha() / 255f);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        bufferbuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        double u = sprite.getInterpolatedU(0.0);
        double v = sprite.getInterpolatedV(0.0);

        int tileY = texTop;
        while (tileY < texHeight) {
            int tileX = texLeft;
            while (tileX < texWidth) {
                double x = left + tileX;
                double y = top + tileY;
                double w = Math.min(tileX + spriteWidth, this.width) - tileX;
                double h = Math.min(tileY + spriteHeight, this.height) - tileY;
                double ur = w / spriteWidth;
                double vb = h / spriteHeight;

                double uu = sprite.getInterpolatedU(ur * 16.0);
                double vv = sprite.getInterpolatedV(vb * 16.0);

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
