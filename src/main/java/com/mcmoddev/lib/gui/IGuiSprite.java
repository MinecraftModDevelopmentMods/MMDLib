package com.mcmoddev.lib.gui;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public interface IGuiSprite {
    IGuiTexture getTexture();

    int getLeft();
    int getTop();
    int getWidth();
    int getHeight();

    boolean needsAlpha();

    @SideOnly(Side.CLIENT)
    default void draw(GuiScreen screen, int left, int top) {
        this.draw(screen, left, top, this.getWidth(), this.getHeight(), false);
    }

    @SideOnly(Side.CLIENT)
    default void draw(GuiScreen screen, int left, int top, int width, int height) {
        this.draw(screen, left, top, width, height, true);
    }

    @SideOnly(Side.CLIENT)
    default void draw(GuiScreen screen, int left, int top, int width, int height, boolean clip) {
//        int offsetX = 0;
//        int offsetY = 0;
//        if (screen instanceof GuiContainer) {
//            GuiContainer container = (GuiContainer)screen;
//            offsetX = container.getGuiLeft();
//            offsetY = container.getGuiTop();
//        }
//
//        int drawLeft = offsetX + left;
//        int drawTop = offsetY + top;

        int texLeft = this.getLeft();
        int texTop = this.getTop();
        int texWidth = this.getWidth();
        int texHeight = this.getHeight();

        int fullLeft = /*drawLeft*/left + (width - texWidth) / 2;
        int fullTop = /*drawTop*/top + (height - texHeight) / 2;

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

    @SideOnly(Side.CLIENT)
    default void draw(GuiScreen screen, int left, int top, int texLeft, int texTop, int texWidth, int texHeight) {
        int offsetX = 0;
        int offsetY = 0;
        if (screen instanceof GuiContainer) {
            GuiContainer container = (GuiContainer)screen;
            offsetX = container.getGuiLeft();
            offsetY = container.getGuiTop();
        }

        int drawLeft = offsetX + left;
        int drawTop = offsetY + top;

        this.getTexture().bind();

        if (this.needsAlpha()) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }

        screen.drawTexturedModalRect(drawLeft, drawTop, texLeft, texTop, texWidth, texHeight);

        if (this.needsAlpha()) {
            GlStateManager.disableBlend();
        }
    }
}
