package zone.moddev.mc.companion.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import zone.moddev.mc.companion.CompanionMod;
import zone.moddev.mc.companion.CompanionWidgets;
import zone.moddev.mc.lib.gui.client.WidgetRenderer;
import zone.moddev.mc.lib.gui.client.WidgetRenderers;

/** Client-only registration and drawing, using only MMDLib's public widget API. */
@EventBusSubscriber(modid = CompanionMod.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CompanionWidgetsClient {
  private CompanionWidgetsClient() {}

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    zone.moddev.mc.lib.gui.client.FilterWidgets.register(
        zone.moddev.mc.companion.CompanionAddons.TAG_MODE,
        access ->
            new zone.moddev.mc.lib.gui.client.FilterWidgets.Instance() {
              public void render(
                  net.minecraft.client.gui.GuiGraphics g,
                  zone.moddev.mc.lib.gui.layout.Rect r,
                  net.minecraft.nbt.CompoundTag config,
                  int mx,
                  int my) {
                var button = new zone.moddev.mc.lib.gui.layout.Rect(r.x(), r.y(), 14, 14);
                g.blitSprite(
                    net.minecraft.resources.ResourceLocation.parse(
                        button.contains(mx, my)
                            ? "mmdlib:button_flat_highlighted"
                            : "mmdlib:button_flat"),
                    button.x(),
                    button.y(),
                    button.width(),
                    button.height());
                g.blitSprite(
                    net.minecraft.resources.ResourceLocation.parse(
                        config.getBoolean("deny")
                            ? "mmdlib:icon_filter_blacklist"
                            : "mmdlib:icon_filter_whitelist"),
                    r.x() + 2,
                    r.y() + 2,
                    10,
                    10);
              }

              public net.minecraft.network.chat.Component tooltip() {
                return net.minecraft.network.chat.Component.translatable(
                    access.configuration().get().getBoolean("deny")
                        ? "screen.mmdlib_companion.filter.blacklist"
                        : "screen.mmdlib_companion.filter.whitelist");
              }

              public boolean click(double x, double y, int button) {
                if (!access.pending().getAsBoolean())
                  access.submit().accept(!access.configuration().get().getBoolean("deny"));
                return true;
              }

              public boolean key(int key, int scan, int modifiers) {
                if (key == 257 || key == 32) return click(0, 0, 0);
                return false;
              }
            });
    WidgetRenderers.GLOBAL.registerInteractive(CompanionWidgets.NAME_EDITOR, NameEditor::new);
    WidgetRenderers.GLOBAL.registerInteractive(CompanionWidgets.RESERVE_SLIDER, ReserveSlider::new);
    WidgetRenderers.GLOBAL.registerInteractive(CompanionWidgets.LANE_SELECTOR, LaneSelector::new);
    WidgetRenderers.GLOBAL.register(
        CompanionWidgets.PROGRESS_DIAL, CompanionWidgetsClient::drawDial);
  }

  private static void drawDial(WidgetRenderer.Context context) {
    var g = context.graphics();
    var r = context.bounds();
    int color =
        0xff000000
            | Integer.parseInt(
                context.widget().properties().getOrDefault("color", "#55CC88").substring(1), 16);
    int cx = r.x() + r.width() / 2, cy = r.y() + r.height() / 2;
    double radius = Math.min(r.width(), r.height()) / 2.0 - 2;
    int lit = (int) Math.round(context.ratio() * 24);
    for (int i = 0; i < 24; i++) {
      double angle = i * Math.PI * 2 / 24 - Math.PI / 2;
      int x = cx + (int) Math.round(Math.cos(angle) * radius);
      int y = cy + (int) Math.round(Math.sin(angle) * radius);
      g.fill(x - 1, y - 1, x + 1, y + 1, i < lit ? color : 0xff454545);
    }
    g.fill(cx - 1, cy - 1, cx + 1, cy + 1, context.hovered() ? 0xffffffff : color);
  }
}
