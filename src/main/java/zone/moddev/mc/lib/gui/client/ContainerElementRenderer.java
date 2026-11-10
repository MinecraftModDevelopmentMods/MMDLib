package zone.moddev.mc.lib.gui.client;

import static zone.moddev.mc.lib.gui.client.ElementDrawing.*;

import net.minecraft.network.chat.Component;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Edge;
import zone.moddev.mc.lib.gui.model.containers.*;

/** Backgrounds and captions for containers; child traversal remains the screen's responsibility. */
public final class ContainerElementRenderer {
  private ContainerElementRenderer() {}

  public static void group(GroupElement element, ElementRenderers.Context c) {
    var r = c.bounds();
    tinted(c.graphics(), "group", r, element.tint().orElse(0xffffff));
    element
        .label()
        .ifPresent(text -> caption(c, text, r.x() + element.padding(), r.y() + element.padding()));
  }

  public static void radioGroup(RadioGroupElement element, ElementRenderers.Context c) {
    element.label().ifPresent(text -> caption(c, text, c.bounds().x(), c.bounds().y()));
  }

  public static void view(ViewElement element, ElementRenderers.Context c) {
    element.title().ifPresent(text -> caption(c, text, c.bounds().x() + 12, c.bounds().y()));
  }

  public static void context(ContextElement element, ElementRenderers.Context c) {
    String selected = element.selected(c.access().state());
    if (!selected.equals(element.defaultView().common().id().orElseThrow()))
      caption(c, Component.literal("‹"), c.bounds().x(), c.bounds().y());
  }

  public static void sideTab(SideTabElement element, ElementRenderers.Context c) {
    var g = c.graphics();
    var r = c.bounds();
    if (element.edge() == Edge.RIGHT) {
      sprite(g, "side_tab", r);
      return;
    }
    g.pose().pushPose();
    try {
      g.pose().translate(r.right(), r.y(), 0);
      g.pose().scale(-1, 1, 1);
      sprite(g, "side_tab", new Rect(0, 0, r.width(), r.height()));
    } finally {
      g.pose().popPose();
    }
  }
}
