package zone.moddev.mc.lib.smoke;

import java.util.Arrays;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Element;

/** Checks actual GL state around buffered solid and fluid gauge draws. */
public final class GuiGaugeSmoke {
  public static void check(MachineScreen screen) {
    var mc = Minecraft.getInstance();
    var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
    boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    int[] factors = factors();
    float[] color = RenderSystem.getShaderColor().clone();
    try {
      var draw =
          Class.forName("zone.moddev.mc.lib.gui.client.GaugeRenderer")
              .getDeclaredMethod(
                  "draw",
                  GuiGraphics.class,
                  Rect.class,
                  Element.Gauge.class,
                  Object.class,
                  Object.class);
      draw.setAccessible(true);
      for (String id : new String[] {"energy", "water"}) {
        var gauge = (Element.Gauge) screen.getMenu().bound.screen().find(id).orElseThrow();
        for (boolean enabled : new boolean[] {false, true}) {
          graphics.drawManaged(
              () -> {
                if (enabled) RenderSystem.enableBlend();
                else RenderSystem.disableBlend();
                if (enabled) RenderSystem.disableDepthTest();
                else RenderSystem.enableDepthTest();
                RenderSystem.blendFuncSeparate(
                    GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE);
                float[] expectedColor = {.7f, .8f, .9f, .6f};
                RenderSystem.setShaderColor(
                    expectedColor[0], expectedColor[1], expectedColor[2], expectedColor[3]);
                // Deliberately leave a GUI fill queued before the immediate gauge texture draws.
                graphics.fill(-100, -100, -90, -90, 0x80ffffff);
                try {
                  draw.invoke(
                      null,
                      graphics,
                      new Rect(-100, -100, 18, 72),
                      gauge,
                      screen.getMenu().value(gauge, false),
                      screen.getMenu().value(gauge, true));
                } catch (ReflectiveOperationException ex) {
                  throw new AssertionError("Gauge draw failed", ex);
                }
                if (GL11.glIsEnabled(GL11.GL_BLEND) != enabled
                    || GL11.glIsEnabled(GL11.GL_DEPTH_TEST) == enabled
                    || !Arrays.equals(
                        factors(), new int[] {GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE})
                    || !Arrays.equals(RenderSystem.getShaderColor(), expectedColor))
                  throw new AssertionError(
                      "Gauge leaked render state: " + id + ", blend=" + enabled);
              });
        }
      }
      MMDLib.LOGGER.info(
          "MMDLIB_GUI_GAUGES_PASS: solid and fluid gauges with buffered fills; blend"
              + " enable/factors, depth and shader color restored");
    } catch (ReflectiveOperationException ex) {
      throw new AssertionError(ex);
    } finally {
      graphics.flush();
      RenderSystem.blendFuncSeparate(factors[0], factors[1], factors[2], factors[3]);
      if (blend) RenderSystem.enableBlend();
      else RenderSystem.disableBlend();
      if (depth) RenderSystem.enableDepthTest();
      else RenderSystem.disableDepthTest();
      RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
    }
  }

  private static int[] factors() {
    return new int[] {
      GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
      GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
      GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
      GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
    };
  }
}
