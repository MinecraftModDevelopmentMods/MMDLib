package zone.moddev.mc.lib.gui.client;

import zone.moddev.mc.lib.gui.model.elements.SpriteElement;

/** Executes the layers selected by a sprite element. */
public final class SpriteRenderer {
  private SpriteRenderer() {}

  public static void draw(SpriteElement element, ElementRenderers.Context context) {
    var bounds = context.bounds();
    var g = context.graphics();
    for (var layer : element.layers(bounds, context.elapsedTicks(), context.ratio())) {
      var clip = layer.clip().offset(bounds.x(), bounds.y());
      if (clip.width() <= 0 || clip.height() <= 0) continue;
      g.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
      g.pose().pushPose();
      try {
        int tint = layer.tint();
        g.setColor(
            ((tint >> 16) & 255) / 255f,
            ((tint >> 8) & 255) / 255f,
            (tint & 255) / 255f,
            ((tint >>> 24) & 255) / 255f);
        g.pose().translate(bounds.x() + bounds.width() / 2f, bounds.y() + bounds.height() / 2f, 0);
        g.pose().mulPose(new org.joml.Quaternionf().rotationZ(layer.rotation()));
        g.pose().translate(-layer.width() / 2f, -layer.height() / 2f, 0);
        g.blitSprite(layer.sprite(), 0, 0, layer.width(), layer.height());
      } finally {
        g.setColor(1, 1, 1, 1);
        g.pose().popPose();
        g.disableScissor();
      }
    }
  }
}
