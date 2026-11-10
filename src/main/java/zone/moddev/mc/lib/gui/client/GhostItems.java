package zone.moddev.mc.lib.gui.client;

import java.util.IdentityHashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Translucent samples retain their item's model, texture and lighting, including opaque blocks. */
final class GhostItems {
  private final Map<Float, Map<RenderType, RenderType>> translucent = new java.util.HashMap<>();

  void render(GuiGraphics graphics, ItemStack stack, int x, int y) {
    render(graphics, stack, x, y, .50f);
  }

  void render(GuiGraphics graphics, ItemStack stack, int x, int y, float alpha) {
    var minecraft = Minecraft.getInstance();
    var renderer = minecraft.getItemRenderer();
    var model = renderer.getModel(stack, minecraft.level, minecraft.player, 0);
    graphics.flush();
    graphics.pose().pushPose();
    try {
      graphics.pose().translate(x + 8, y + 8, 150);
      graphics.pose().scale(16, -16, 16);
      if (!model.usesBlockLight()) Lighting.setupForFlatItems();
      renderer.render(
          stack,
          ItemDisplayContext.GUI,
          false,
          graphics.pose(),
          type ->
              graphics
                  .bufferSource()
                  .getBuffer(
                      translucent
                          .computeIfAbsent(alpha, ignored -> new IdentityHashMap<>())
                          .computeIfAbsent(type, original -> fade(original, alpha))),
          15728880,
          OverlayTexture.NO_OVERLAY,
          model);
      graphics.flush();
    } finally {
      graphics.pose().popPose();
      RenderSystem.setShaderColor(1, 1, 1, 1);
      Lighting.setupFor3DItems();
    }
  }

  static RenderType fade(RenderType original, float alpha) {
    // A shader alpha alone is ignored by opaque/cutout item passes. Enable blending after each
    // original pass sets its state, and reset before the following normal inventory item draws.
    return new RenderType(
        "mmdlib_ghost",
        original.format(),
        original.mode(),
        original.bufferSize(),
        original.affectsCrumbling(),
        original.sortOnUpload(),
        () -> {
          original.setupRenderState();
          RenderSystem.enableBlend();
          RenderSystem.defaultBlendFunc();
          RenderSystem.setShaderColor(1, 1, 1, alpha);
        },
        () -> {
          RenderSystem.setShaderColor(1, 1, 1, 1);
          RenderSystem.disableBlend();
          original.clearRenderState();
        }) {};
  }
}
