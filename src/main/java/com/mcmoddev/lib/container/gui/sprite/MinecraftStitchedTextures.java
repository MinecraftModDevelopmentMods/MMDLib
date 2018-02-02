package com.mcmoddev.lib.container.gui.sprite;

import com.mcmoddev.lib.container.gui.IGuiTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;

public class MinecraftStitchedTextures implements IGuiTexture {
    public static final MinecraftStitchedTextures INSTANCE = new MinecraftStitchedTextures();

    private MinecraftStitchedTextures() {}

    @Override
    public ResourceLocation getResource() {
        return TextureMap.LOCATION_BLOCKS_TEXTURE;
    }
}
