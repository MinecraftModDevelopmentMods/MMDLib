package zone.moddev.mc.companion.client;

import net.minecraft.client.Minecraft;

import zone.moddev.mc.companion.CompanionWidgets;
import zone.moddev.mc.lib.gui.client.WidgetAccess;
import zone.moddev.mc.lib.gui.client.WidgetInstance;

/** Drag previews stay local; only release commits. Arrow keys and scroll commit one step. */
final class ReserveSlider implements WidgetInstance {
  private final WidgetAccess access;
  private int preview;
  private boolean dragging, focused, unconfirmed;

  ReserveSlider(WidgetAccess access) {
    this.access = access;
  }

  private int confirmed() {
    return access.value() instanceof Number value ? value.intValue() : 0;
  }

  private void at(double x) {
    var r = access.bounds();
    preview = (int) Math.clamp(Math.round((x - r.x() - 3) * 100 / (r.width() - 6)), 0, 100);
  }

  private void commit() {
    access.submit(CompanionWidgets.RESERVE, preview);
  }

  @Override
  public void render(Context context) {
    if (!dragging && !access.pending()) preview = confirmed();
    var r = context.bounds();
    var g = context.graphics();
    g.drawString(
        Minecraft.getInstance().font,
        WidgetDrawing.text(unconfirmed ? "reserve.unconfirmed" : "reserve", preview),
        r.x(),
        r.y(),
        0xff404040,
        false);
    g.fill(r.x(), r.y() + 15, r.right(), r.y() + 28, focused ? 0xfff5d477 : 0xff444444);
    g.fill(r.x() + 1, r.y() + 16, r.right() - 1, r.y() + 27, 0xff727272);
    int knob = r.x() + 3 + (r.width() - 6) * preview / 100;
    g.fill(knob - 2, r.y() + 16, knob + 2, r.y() + 27, access.pending() ? 0xffaaaaaa : 0xff66cc88);
  }

  @Override
  public boolean focusable() {
    return true;
  }

  @Override
  public void setFocused(boolean value) {
    focused = value;
  }

  @Override
  public Click mouseClicked(double x, double y, int button) {
    if (button != 0 || access.pending()) return Click.CONSUME;
    unconfirmed = false;
    dragging = true;
    at(x);
    return Click.CAPTURE;
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    at(x);
    return true;
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    if (dragging) {
      at(x);
      dragging = false;
      commit();
    }
    return true;
  }

  @Override
  public void cancelInteraction() {
    dragging = false;
    preview = confirmed();
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (key != 263 && key != 262 && key != 268 && key != 269) return false;
    if (access.pending()) return true;
    preview =
        key == 268
            ? 0
            : key == 269
                ? 100
                : Math.clamp(
                    confirmed() + (key == 262 ? 1 : -1) * ((modifiers & 1) != 0 ? 10 : 1), 0, 100);
    commit();
    return true;
  }

  @Override
  public boolean mouseScrolled(double x, double y, double dx, double dy) {
    if (!access.pending() && dy != 0) {
      preview = Math.clamp(confirmed() + (dy > 0 ? 1 : -1), 0, 100);
      commit();
    }
    return true;
  }

  @Override
  public void actionResult(boolean accepted) {
    unconfirmed = !accepted;
    preview = confirmed();
  }
}
