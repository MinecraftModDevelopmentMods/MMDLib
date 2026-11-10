package zone.moddev.mc.lib.gui.client;

import static zone.moddev.mc.lib.gui.client.ElementDrawing.*;

import zone.moddev.mc.lib.gui.model.elements.ButtonControl;

/** Shared presentation for built-in and mod-defined buttons. */
public final class ButtonRenderer {
  private ButtonRenderer() {}

  public static void draw(ButtonControl button, ElementRenderers.Context c) {
    var g = c.graphics();
    var r = c.bounds();
    sprite(
        g,
        button.flat()
            ? c.hovered() ? "button_flat_highlighted" : "button_flat"
            : c.hovered() ? "minecraft:widget/button_highlighted" : "minecraft:widget/button",
        r);
    button
        .text()
        .ifPresent(
            text ->
                g.drawCenteredString(
                    c.access().font(),
                    c.access().text(text),
                    r.x() + r.width() / 2,
                    r.y() + 6,
                    0xffffff));
    int size = button.flat() ? 12 : 16;
    button
        .iconFor(c.value())
        .ifPresent(
            icon ->
                blit(
                    g,
                    icon,
                    r.x() + (r.width() - size) / 2,
                    r.y() + (r.height() - size) / 2,
                    size,
                    size));
    button
        .badge(c.value(), c.maximum())
        .ifPresent(icon -> g.blitSprite(icon, r.right() - 6, r.bottom() - 6, 5, 5));
  }
}
