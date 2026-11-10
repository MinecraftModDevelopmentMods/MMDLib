package zone.moddev.mc.lib.gui.client;

import java.util.List;

import net.minecraft.network.chat.Component;

import zone.moddev.mc.lib.api.automation.FilterControl;
import zone.moddev.mc.lib.gui.layout.Rect;

final class FilterNumberBehavior implements ElementBehavior {
  private final FilterControl.Number element;
  private final ElementAccess access;

  FilterNumberBehavior(FilterControl.Number element, ElementAccess access) {
    this.element = element;
    this.access = access;
  }

  public boolean focusable() {
    return true;
  }

  private void step(boolean forward) {
    long current = FilterDrawing.configuration(access.value()).getInt(element.id());
    FilterEdits.submit(
        access,
        element,
        0,
        Long.toString(
            Math.clamp(
                current + (forward ? (long) element.step() : -(long) element.step()),
                element.min(),
                element.max())));
  }

  public Click mouseClicked(double x, double y, int button) {
    if (button < 0 || button > 1) return Click.PASS;
    step(x >= access.bounds().x() + access.bounds().width() / 2);
    return Click.CONSUME;
  }

  public boolean keyPressed(int key, int scan, int modifiers) {
    if (key != 262 && key != 263) return false;
    step(key == 262);
    return true;
  }

  public List<Component> tooltip(int x, int y) {
    return List.of(element.tooltip());
  }

  static void render(FilterControl.Number element, ElementRenderers.Context c) {
    var r = c.bounds();
    var g = c.graphics();
    FilterDrawing.button(g, r);
    g.drawCenteredString(
        c.access().font(),
        Integer.toString(FilterDrawing.configuration(c.value()).getInt(element.id())),
        r.x() + r.width() / 2,
        r.y() + 5,
        0xffffff);
    FilterDrawing.arrow(g, new Rect(r.x() + 2, r.y() + 4, 6, 8), false);
    FilterDrawing.arrow(g, new Rect(r.right() - 8, r.y() + 4, 6, 8), true);
  }
}
