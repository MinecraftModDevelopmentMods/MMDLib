package zone.moddev.mc.lib.gui.client;

import java.util.Optional;

import zone.moddev.mc.lib.gui.model.Element;

/**
 * Parent behavior capability shared by option drawing and input; implementations own selection
 * policy.
 */
public interface OptionSelection {
  boolean selected(Element option);

  /** Return CONSUME for a recognized option, even when a pending action prevents submission. */
  ElementBehavior.Click activate(Element option, int button, int modifiers);

  static Optional<OptionSelection> parent(Element option, ElementRenderAccess access) {
    return access
        .parent(option)
        .flatMap(parent -> access.capability(parent, OptionSelection.class));
  }
}
