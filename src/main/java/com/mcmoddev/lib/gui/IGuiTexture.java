package com.mcmoddev.lib.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public interface IGuiTexture {
    ResourceLocation getResource();

    @SideOnly(Side.CLIENT)
    default void bind() {
        Minecraft.getMinecraft().getTextureManager().bindTexture(this.getResource());
    }
}
