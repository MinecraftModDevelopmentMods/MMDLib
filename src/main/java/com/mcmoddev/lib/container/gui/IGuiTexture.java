package com.mcmoddev.lib.container.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Provides a way to generically use a texture.
 */
public interface IGuiTexture {
    /**
     * Returns the resource location for this texture.
     * @return The resource location for this texture.
     */
    ResourceLocation getResource();

    /**
     * Binds the texture in minecraft's texture manager.
     */
    @SideOnly(Side.CLIENT)
    default void bind() {
        Minecraft.getMinecraft().getTextureManager().bindTexture(this.getResource());
    }
}
