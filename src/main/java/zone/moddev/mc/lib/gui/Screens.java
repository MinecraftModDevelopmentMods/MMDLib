package zone.moddev.mc.lib.gui;

import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.load.ScreenBinder;

/**
 * Every loaded screen, by ID. A block entity's screen has the same ID as its block-entity type's
 * registry name. Filled once at startup by MMDLib; empty before that.
 */
public final class Screens {
  private static volatile Map<ResourceLocation, ScreenBinder.Bound> screens = Map.of();

  private Screens() {}

  /** Every loaded screen, by ID. */
  public static Map<ResourceLocation, ScreenBinder.Bound> all() {
    return screens;
  }

  /** The screen with {@code id}, if one was loaded. */
  public static Optional<ScreenBinder.Bound> get(ResourceLocation id) {
    return Optional.ofNullable(screens.get(id));
  }

  /** Replaces the loaded screens; called by MMDLib at startup. */
  public static void install(Map<ResourceLocation, ScreenBinder.Bound> loaded) {
    screens = Map.copyOf(loaded);
  }
}
