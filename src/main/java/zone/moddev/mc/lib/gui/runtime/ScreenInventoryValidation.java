package zone.moddev.mc.lib.gui.runtime;

import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.load.ScreenValidationContext;
import zone.moddev.mc.lib.gui.model.Screen;

/** Validates screen references against the actual host, before either side constructs slots. */
public final class ScreenInventoryValidation {
  private ScreenInventoryValidation() {}

  /** Hosts can call this after constructing their storages; menus repeat validation on opening. */
  public static void validate(net.minecraft.world.level.block.entity.BlockEntity owner) {
    if (!(owner instanceof ScreenHost host)) return;
    var id =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(owner.getType());
    if (id != null)
      zone.moddev.mc.lib.gui.Screens.get(id).ifPresent(bound -> validate(bound.screen(), host));
  }

  public static void validate(Screen screen, ScreenHost host) {
    var context = new ScreenValidationContext(screen);
    for (var element : screen.elements()) {
      try {
        element.validate();
        element.validateResources(host.screenResourceCounts());
        element.validateHost(context, host);
      } catch (ScreenException failure) {
        throw failure;
      } catch (IllegalArgumentException failure) {
        throw new zone.moddev.mc.lib.gui.load.ScreenException(
            screen.source(element.common()), element.common().line(), failure.getMessage());
      }
    }
    // Highlights are optional decoration: a shared group may name elements absent from this screen.
  }
}
