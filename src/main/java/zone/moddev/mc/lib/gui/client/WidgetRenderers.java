package zone.moddev.mc.lib.gui.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.model.elements.CustomElement;

/** Client-only renderer registry. Register during client mod construction or client setup. */
public final class WidgetRenderers {
  public static final WidgetRenderers GLOBAL = new WidgetRenderers();

  private final Map<ResourceLocation, WidgetFactory> renderers = new HashMap<>();
  private boolean frozen;

  /** Registers a renderer; duplicate IDs and registration after load completion are errors. */
  public synchronized void register(ResourceLocation type, WidgetRenderer renderer) {
    Objects.requireNonNull(renderer, "renderer");
    registerInteractive(
        type,
        access ->
            new WidgetInstance() {
              @Override
              public void render(WidgetRenderer.Context context) {
                renderer.render(context);
              }
            });
  }

  /** Registers a factory; it must create independent state for each widget in each screen. */
  public synchronized void registerInteractive(ResourceLocation type, WidgetFactory renderer) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(renderer, "renderer");
    if (frozen)
      throw new IllegalStateException("Widget renderers are frozen; register during client setup");
    if (renderers.putIfAbsent(type, renderer) != null)
      throw new IllegalArgumentException("Duplicate widget renderer: " + type);
  }

  public synchronized WidgetFactory require(ResourceLocation type) {
    var renderer = renderers.get(type);
    if (renderer == null) throw new IllegalArgumentException("Missing widget renderer: " + type);
    return renderer;
  }

  /** Adapts widget factories to the ordinary element renderer and validation lifecycle. */
  public ElementRenderers.Renderer<CustomElement> elementRenderer() {
    return new ElementRenderers.Renderer<>() {
      @Override
      public void validate(CustomElement element) {
        require(element.type());
      }

      @Override
      public void render(CustomElement element, ElementRenderers.Context c) {
        c.access()
            .capability(element, WidgetRenderer.class)
            .orElseThrow()
            .render(
                new WidgetRenderer.Context(
                    c.graphics(),
                    element,
                    c.bounds(),
                    c.value(),
                    c.maximum(),
                    c.mouseX(),
                    c.mouseY(),
                    c.partialTick()));
      }
    };
  }

  /** Freezes registration and catches renderer IDs with no common-side definition. */
  public synchronized void freeze(WidgetTypes types) {
    renderers.keySet().forEach(types::require);
    frozen = true;
  }

  @EventBusSubscriber(modid = "mmdlib", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
  public static final class Registration {
    @SubscribeEvent
    public static void complete(FMLLoadCompleteEvent event) {
      event.enqueueWork(() -> GLOBAL.freeze(WidgetTypes.GLOBAL));
    }
  }
}
