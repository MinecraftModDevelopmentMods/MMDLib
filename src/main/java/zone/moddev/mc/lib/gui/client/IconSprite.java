package zone.moddev.mc.lib.gui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

/**
 * A 16-pixel drawer icon, supplied by a GUI sprite, an atlas sprite, or an item
 * model.
 */
@FunctionalInterface
public interface IconSprite {
    void draw(GuiGraphics graphics, int x, int y);

    static IconSprite gui(ResourceLocation sprite) {
        return (graphics, x, y) -> ElementDrawing.blit(graphics, sprite, x, y,
            16, 16);
    }

    static IconSprite atlas(ResourceLocation sprite) {
        return (graphics, x, y) -> graphics.blit(x, y, 0, 16, 16,
            Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(sprite));
    }

    static IconSprite item(java.util.function.Supplier<ItemStack> item) {
        return (graphics, x, y) -> graphics.renderItem(item.get(), x, y);
    }
}
