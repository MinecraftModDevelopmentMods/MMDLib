package zone.moddev.mc.lib.gui.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/** Visual reservations only: preview items never become slot contents. */
final class ReservationOverlay {
  private static final float COUNT_SCALE = .875f;
  private final GhostItems ghosts = new GhostItems();

  void render(
      GuiGraphics g,
      Font font,
      Slot slot,
      zone.moddev.mc.lib.gui.model.elements.SlotElement element,
      ItemStack displayed,
      SlotPlan plan,
      String countString) {
    element.renderOverlay(
        new zone.moddev.mc.lib.gui.model.elements.SlotElement.OverlayPainter() {
          public void ghost(ItemStack stack, float alpha) {
            ghosts.render(g, stack, slot.x, slot.y, alpha);
          }

          public void quantity(String text, boolean bottom) {
            label(g, font, text, slot.x, slot.y + (bottom ? 9 : 0));
          }
        },
        displayed,
        plan,
        countString);
  }

  private static void label(GuiGraphics g, Font font, String text, int x, int y) {
    g.pose().pushPose();
    g.pose().translate(x + 17 - font.width(text) * COUNT_SCALE, y, 200);
    g.pose().scale(COUNT_SCALE, COUNT_SCALE, 1);
    g.drawString(font, text, 0, 0, 0xffffffff, true);
    g.pose().popPose();
  }
}
