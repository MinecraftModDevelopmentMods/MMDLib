package zone.moddev.mc.lib.gui.client;

import java.util.Objects;

import zone.moddev.mc.lib.gui.ElementActions;
import zone.moddev.mc.lib.gui.model.Navigation;

/** Common click bindings and recipe links, reusable by mod behavior implementations. */
public class DefaultElementBehavior implements ElementBehavior {
  protected final ElementAccess access;
  private Navigation pendingNavigation;

  public DefaultElementBehavior(ElementAccess access) {
    this.access = Objects.requireNonNull(access);
  }

  protected boolean directInteraction() {
    return true;
  }

  protected Click click(double x, double y, int button) {
    var binding = access.element().navigation().orElse(null);
    if (binding == null) return Click.PASS;
    var destination = binding.resolve(access.presentation()::find).orElse(null);
    if (destination == null) return Click.PASS;
    if (access.click(Navigation.NAVIGATE, button, 0)) {
      pendingNavigation = binding;
      NavigationMemory.navigate(
          access,
          binding.target().context(),
          binding.destination(destination, access.presentation().state()));
    }
    return Click.CONSUME;
  }

  public void actionResult(boolean accepted) {
    var binding =
        pendingNavigation != null ? pendingNavigation : access.element().navigation().orElse(null);
    if (binding != null) {
      binding
          .resolve(access.presentation()::find)
          .ifPresent(destination -> NavigationMemory.remember(access, destination));
    }
    pendingNavigation = null;
  }

  public Click mouseClicked(double x, double y, int button) {
    if (button < 0 || button > 1) return Click.PASS;
    if (directInteraction() && access.element().interactive()) {
      access.click(ElementActions.CLICK, button, 0);
      return Click.CONSUME;
    }
    var result = click(x, y, button);
    if (result != Click.PASS) return result;
    if (!access.element().common().jeiRecipes().isEmpty() && access.showRecipes())
      return Click.CONSUME;
    if (access.element().common().clicks().any()) {
      access.click(ElementActions.CLICK, button, 0);
      return Click.CONSUME;
    }
    return Click.PASS;
  }
}
