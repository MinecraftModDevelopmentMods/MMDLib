package zone.moddev.mc.lib.gui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;

/** Client drawing primitives; SlotElement decides backgrounds, icons and ghost visibility. */
final class SlotRenderer {
  private final GhostItems ghosts = new GhostItems();

  void draw(
      GuiGraphics graphics, Rect bounds, SlotElement element, ItemStack contents, ItemStack hint) {
    int[] item = element.itemPosition(bounds);
    element.render(
        new SlotElement.Painter() {
          public void background(ResourceLocation sprite, int tint) {
            graphics.setColor(
                (tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f, (tint & 255) / 255f, 1);
            graphics.blitSprite(sprite, bounds.x(), bounds.y(), bounds.width(), bounds.height());
            graphics.setColor(1, 1, 1, 1);
          }

          public void icon(ResourceLocation sprite, java.util.Optional<ResourceLocation> atlas) {
            int size = element.appearance().itemSize();
            if (atlas.isEmpty()) graphics.blitSprite(sprite, item[0], item[1], size, size);
            else
              graphics.blit(
                  item[0],
                  item[1],
                  0,
                  size,
                  size,
                  net.minecraft.client.Minecraft.getInstance()
                      .getTextureAtlas(atlas.get())
                      .apply(sprite));
          }

          public void ghost(ItemStack stack, float alpha) {
            graphics.pose().pushPose();
            graphics.pose().translate(item[0], item[1], 0);
            float scale = element.appearance().itemSize() / 16f;
            graphics.pose().scale(scale, scale, 1);
            ghosts.render(graphics, stack, 0, 0, alpha);
            graphics.pose().popPose();
          }
        },
        contents,
        hint);
  }
}
