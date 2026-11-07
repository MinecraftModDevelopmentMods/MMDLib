package zone.moddev.mc.lib.gui.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/** Visual reservations only: preview items never become slot contents. */
final class ReservationOverlay {
  private static final float COUNT_SCALE = .875f;
  private final GhostItems ghosts = new GhostItems();
  private final Map<Integer, Preview> previews = new HashMap<>();

  private static final class Preview {
    final SlotPlan plan;
    final ItemStack output;
    final String label;

    Preview(SlotPlan plan) {
      this.plan = plan;
      output = plan.add();
      String input = plan.take() > 0 ? "-" + plan.take() : "";
      String added = output.isEmpty() ? "" : "+" + output.getCount();
      label = input + (!input.isEmpty() && !added.isEmpty() ? " " : "") + added;
    }
  }

  void render(
      GuiGraphics g,
      Font font,
      Slot slot,
      ItemStack displayed,
      SlotPlan plan,
      @org.jetbrains.annotations.Nullable String countString) {
    if (plan.isNone()) {
      previews.remove(slot.index);
    } else {
      var preview = previews.get(slot.index);
      if (preview == null || preview.plan != plan) {
        preview = new Preview(plan);
        previews.put(slot.index, preview);
      }
      // Vanilla may be showing a drag preview in an otherwise empty slot. Keep that preview.
      if (displayed.isEmpty() && !preview.output.isEmpty())
        ghosts.render(g, preview.output, slot.x, slot.y);
      label(g, font, preview.label, slot.x, slot.y);
    }
    // Match vanilla's count visibility and any coloured drag-limit override.
    if (!displayed.isEmpty() && (displayed.getCount() != 1 || countString != null))
      label(
          g,
          font,
          countString == null ? Integer.toString(displayed.getCount()) : countString,
          slot.x,
          slot.y + 9);
  }

  private static void label(GuiGraphics g, Font font, String text, int x, int y) {
    g.pose().pushPose();
    g.pose().translate(x + 17 - font.width(text) * COUNT_SCALE, y, 200);
    g.pose().scale(COUNT_SCALE, COUNT_SCALE, 1);
    g.drawString(font, text, 0, 0, 0xffffffff, true);
    g.pose().popPose();
  }
}
