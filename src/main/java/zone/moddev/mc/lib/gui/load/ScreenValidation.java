package zone.moddev.mc.lib.gui.load;

import java.util.*;

import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.model.*;

/**
 * Tree-wide checks shared by XML loading and Java screen binding. Local rules belong to elements.
 */
public final class ScreenValidation {
  private ScreenValidation() {}

  // Checks a schema cannot express

  public static void check(Screen screen, WidgetTypes widgets) {
    var ids = new HashMap<String, Element>();
    var context = new ScreenValidationContext(screen);
    for (var element : screen.elements()) {
      var c = element.common();
      var source = screen.source(c);
      if (c.id().isPresent() && ids.putIfAbsent(c.id().get(), element) != null)
        throw new ScreenException(source, c.line(), "Duplicate id: " + c.id().get());
    }
    for (var element : screen.elements()) {
      var c = element.common();
      var source = screen.source(c);
      int line = c.line();
      try {
        element.validate(widgets);
        element.validateScreen(context);
      } catch (ScreenException failure) {
        throw failure;
      } catch (RuntimeException failure) {
        var name = element.validationName();
        throw new ScreenException(
            source, line, (name.isEmpty() ? "" : name + ": ") + failure.getMessage(), failure);
      }
      if (c.hoverShows().isPresent() && !ids.containsKey(c.hoverShows().get()))
        throw new ScreenException(
            source, line, "hover-shows names no element: " + c.hoverShows().get());
      if (c.max().isPresent() && c.value().isEmpty())
        throw new ScreenException(source, line, "max needs value");
    }
    checkSpacers(screen);
    for (var element : screen.body()) checkFill(screen, element, true);
    for (var element : screen.tabs()) checkFill(screen, element, true);
  }

  /**
   * A {@code fill} length matches the box around it across, so a vertical gauge or meter must sit
   * directly in an {@code hbox}, and a horizontal one in a vertical stack (a {@code vbox}, group,
   * view, side tab, or the screen itself).
   *
   * @param verticalParent whether the element's parent stacks its children vertically
   */
  private static void checkFill(Screen screen, Element element, boolean verticalParent) {
    var source = screen.source(element.common());
    Orientation orientation = element.fillOrientation().orElse(null);
    if (orientation == Orientation.VERTICAL && verticalParent)
      throw new ScreenException(
          source,
          element.common().line(),
          "A vertical length=\"fill\" matches the height of its row, so it belongs directly in"
              + " an <hbox>");
    if (orientation == Orientation.HORIZONTAL && !verticalParent)
      throw new ScreenException(
          source,
          element.common().line(),
          "A horizontal length=\"fill\" matches the width of its column, so it belongs directly"
              + " in a <vbox>, <group>, <view> or <side-tab>");
    boolean vertical = element.verticalChildren(verticalParent);
    for (var child : element.children()) checkFill(screen, child, vertical);
  }

  private static void checkSpacers(Screen screen) {
    // A spacer only has room to fill inside a box.
    for (var element : screen.elements())
      if (!element.acceptsSpacers())
        for (var child : element.children())
          if (child.spacer())
            throw new ScreenException(
                screen.source(child.common()),
                child.common().line(),
                "<spacer> belongs directly inside a vbox or hbox");
    for (var element : screen.body())
      if (element.spacer())
        throw new ScreenException(
            screen.source(element.common()),
            element.common().line(),
            "<spacer> belongs directly inside a vbox or hbox");
  }
}
