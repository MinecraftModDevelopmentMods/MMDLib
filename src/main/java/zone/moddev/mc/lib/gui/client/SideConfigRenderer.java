package zone.moddev.mc.lib.gui.client;

import static zone.moddev.mc.lib.gui.client.ElementDrawing.*;

import net.minecraft.network.chat.Component;

import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.elements.SideConfigElement;

/** Side configuration visuals; selections and face modes are read-only presentation inputs. */
public final class SideConfigRenderer {
  private SideConfigRenderer() {}

  public static void draw(SideConfigElement element, ElementRenderers.Context c) {
    var g = c.graphics();
    var r = c.bounds();
    var access = c.access();
    int group = access.capability(element, SideConfigPresentation.class).orElseThrow().selection();
    sprite(g, "minecraft:widget/button", new Rect(r.x(), r.y(), r.width(), 20));
    var target = access.find(element.groups().get(group)).orElseThrow();
    var label =
        access
            .groupLabel(target)
            .map(access::text)
            .orElseGet(() -> Component.translatable("screen.mmdlib.side_config"));
    g.drawCenteredString(
        access.font(),
        access.font().plainSubstrByWidth(label.getString(), r.width() - 6),
        r.x() + r.width() / 2,
        r.y() + 6,
        0xffffff);
    int[] colors = {0x777777, 0x679fea, 0xef994e, 0xad6fea};
    for (int i = 0; i < 6; i++) {
      var face = element.face(r, i);
      tinted(
          g,
          "slot",
          face,
          colors[
              Math.clamp(
                  access
                      .capability(element, SideConfigPresentation.class)
                      .orElseThrow()
                      .sideMode(group, i),
                  0,
                  3)]);
      g.drawCenteredString(
          access.font(),
          Component.translatable(
              "screen.mmdlib.face.short." + RelativeFace.values()[i].getSerializedName()),
          face.x() + 10,
          face.y() + 6,
          0xffffff);
    }
  }

  public static void highlight(SideConfigElement element, ElementRenderers.Context c) {
    c.access()
        .find(
            element
                .groups()
                .get(
                    c.access()
                        .capability(element, SideConfigPresentation.class)
                        .orElseThrow()
                        .selection()))
        .ifPresent(
            target ->
                c.access().highlight(c.graphics(), target, ElementHighlight.selection(0xfff0cf55)));
  }
}
