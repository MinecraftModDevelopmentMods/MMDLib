package zone.moddev.mc.lib.gui.client;

import java.util.List;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import zone.moddev.mc.lib.api.automation.FilterControl;
import zone.moddev.mc.lib.gui.layout.Rect;

final class FilterTextBehavior implements ElementBehavior, FilterControlPresentation {
  private final FilterControl.Text element;
  private final ElementAccess access;
  private final EditBox field;

  FilterTextBehavior(FilterControl.Text element, ElementAccess access) {
    this.element = element;
    this.access = access;
    field = new EditBox(access.presentation().font(), 0, 0, 80, 16, element.tooltip());
    field.setMaxLength(element.maxLength());
    sync();
  }

  private void sync() {
    var r = access.bounds();
    field.setX(r.x());
    field.setY(r.y());
    field.setWidth(Math.max(12, r.width() - 12));
    if (!field.isFocused())
      field.setValue(FilterDrawing.configuration(access.value()).getString(element.id()));
  }

  public boolean focusable() {
    return true;
  }

  public void setFocused(boolean focused) {
    field.setFocused(focused);
  }

  public void tick() {
    sync();
  }

  public Click mouseClicked(double x, double y, int button) {
    if (button < 0 || button > 1) return Click.PASS;
    sync();
    if (x >= access.bounds().right() - 12) {
      FilterEdits.submit(access, element, 0, field.getValue());
      return Click.CONSUME;
    }
    field.setFocused(true);
    field.mouseClicked(x, y, button);
    return Click.CAPTURE;
  }

  public boolean keyPressed(int key, int scan, int modifiers) {
    if (key == 256) return false;
    if (key == 257 || key == 335) {
      FilterEdits.submit(access, element, 0, field.getValue());
      return true;
    }
    return field.keyPressed(key, scan, modifiers);
  }

  public boolean charTyped(char c, int modifiers) {
    return field.charTyped(c, modifiers);
  }

  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    return field.mouseDragged(x, y, button, dx, dy);
  }

  public boolean mouseReleased(double x, double y, int button) {
    return field.mouseReleased(x, y, button);
  }

  public void cancelInteraction() {
    field.setFocused(false);
  }

  public List<Component> tooltip(int x, int y) {
    return List.of(element.tooltip());
  }

  public void render(ElementRenderers.Context c) {
    sync();
    field.render(c.graphics(), c.mouseX(), c.mouseY(), c.partialTick());
    var r = c.bounds();
    var submit = new Rect(r.right() - 10, r.y() + 3, 8, 10);
    FilterDrawing.button(c.graphics(), submit);
    FilterDrawing.arrow(c.graphics(), submit, true);
  }
}
