package com.mcmoddev.lib.gui.sprite;

import java.awt.Color;
import com.mcmoddev.lib.gui.IGuiSprite;
import com.mcmoddev.lib.gui.IGuiTexture;
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
    public boolean needsAlpha() { return false; }

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

        int startX = texLeft % spriteWidth;
        int startY = texTop % spriteHeight;

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

        int tileY = 0;
        while (tileY < texHeight) {
            int tileX = 0;
            int texX = startX;
            while (tileX < texWidth) {
//                bufferbuilder.pos((double)x, (double)(y + height), 0.0D).tex((double)(u * f), (double)((v + (float)height) * f1)).endVertex();
//                bufferbuilder.pos((double)(x + width), (double)(y + height), 0.0D).tex((double)((u + (float)width) * f), (double)((v + (float)height) * f1)).endVertex();
//                bufferbuilder.pos((double)(x + width), (double)y, 0.0D).tex((double)((u + (float)width) * f), (double)(v * f1)).endVertex();
//                bufferbuilder.pos((double)x, (double)y, 0.0D).tex((double)(u * f), (double)(v * f1)).endVertex();
            }
        }

        tessellator.draw();
    }
}
