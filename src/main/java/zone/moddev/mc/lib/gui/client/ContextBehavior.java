package zone.moddev.mc.lib.gui.client;

import zone.moddev.mc.lib.gui.model.containers.ContextElement;

/** Context back controls and per-screen remembered views. Definitions remain immutable. */
public class ContextBehavior extends NavigableBehavior {
  private final ContextElement context;

  public ContextBehavior(ContextElement context, ElementAccess access) {
    super(context, access);
    this.context = context;
  }

  private String id() {
    return context.common().id().orElseThrow();
  }

  private String defaultView() {
    return context.defaultView().common().id().orElseThrow();
  }

  private boolean back() {
    if (context.selected(access.presentation().state()).equals(defaultView())) return false;
    if (access.click(ContextElement.BACK, 0, 0)) {
      NavigationMemory.navigate(access, id(), defaultView());
    }
    return true;
  }

  protected Click click(double x, double y, int button) {
    var bounds = access.bounds();
    return x < bounds.x() + 12 && y < bounds.y() + 12 && back() ? Click.CONSUME : Click.PASS;
  }

  public boolean shortcut(int key, int scan, int modifiers) {
    return key == 256 && back();
  }
}
