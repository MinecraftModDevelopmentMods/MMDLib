package zone.moddev.mc.lib.gui.client;

import java.util.function.Consumer;

import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.ScreenHover;

/** Shared adapters for declarative hover callbacks and hover-shows presentation. */
public final class ElementHover {
  private ElementHover() {}

  /** Machine-backed hosts supply the menu context; hover tracking itself has no menu dependency. */
  public static void dispatch(MachineMenu menu, Element element, boolean entered) {
    element
        .common()
        .onHover()
        .ifPresent(
            handler ->
                zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL
                    .events()
                    .dispatch(
                        handler,
                        zone.moddev.mc.lib.api.MaterialEventTypes.GUI_HOVER,
                        new ScreenHover(menu, element, entered)));
  }

  public static void preview(Element hovered, ElementRenderAccess access, Consumer<Element> draw) {
    if (hovered != null) hovered.common().hoverShows().flatMap(access::find).ifPresent(draw);
  }
}
