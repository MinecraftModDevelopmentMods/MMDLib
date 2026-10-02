package zone.moddev.mc.mmdlib.container.gui.sprite;

import zone.moddev.mc.mmdlib.container.gui.IGuiTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;

/**
 * {@link IGuiTexture} representing the {@link TextureMap#LOCATION_BLOCKS_TEXTURE}.
 */
public class MinecraftStitchedTextures implements IGuiTexture {
    /**
     * The unique instance of MinecraftStitchedTextures.
     */
    public static final MinecraftStitchedTextures INSTANCE = new MinecraftStitchedTextures();

    private MinecraftStitchedTextures() {}

    @Override
    public ResourceLocation getResource() {
        return TextureMap.LOCATION_BLOCKS_TEXTURE;
    }
}
