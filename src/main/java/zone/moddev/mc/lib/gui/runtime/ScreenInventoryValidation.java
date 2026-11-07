package zone.moddev.mc.lib.gui.runtime;

import zone.moddev.mc.lib.gui.model.Element;
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
    int count = host.screenItems().getSlots();
    for (var element : screen.elements()) {
      if (element instanceof Element.Button button && button.processorKey().isPresent()) {
        var key = button.processorKey().orElseThrow();
        if (host.screenFeatures() == null || host.screenFeatures().processor(key).isEmpty())
          throw new zone.moddev.mc.lib.gui.load.ScreenException(
              screen.source(button.common()),
              button.common().line(),
              "Unknown processor-key: " + key);
      }
      if (element instanceof Element.Slot slot
          && slot.kind() != Element.SlotKind.GHOST
          && (slot.index() < 0 || slot.index() >= count))
        throw new IllegalArgumentException(
            screen.id()
                + ": slot "
                + slot.common().id().orElse("<unnamed>")
                + " index "
                + slot.index()
                + " outside storage of size "
                + count);
    }
    var automation = host.screenAutomation();
    if (automation == null) return;
    for (var group : automation.groups())
      for (String highlight : group.highlights())
        if (screen.find(highlight).isEmpty())
          throw new IllegalArgumentException(
              screen.id()
                  + ": resource group '"
                  + group.id()
                  + "' references missing screen element '"
                  + highlight
                  + "'");
  }
}
