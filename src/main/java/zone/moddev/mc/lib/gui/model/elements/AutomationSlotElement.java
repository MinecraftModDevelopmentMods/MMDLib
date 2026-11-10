package zone.moddev.mc.lib.gui.model.elements;

import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.runtime.slot.MenuSlots;

/** Default real addon/filter slot. Mods can override its factory, geometry and transfer role. */
public class AutomationSlotElement extends ItemSlotElement {
  public AutomationSlotElement(Common common, int index, Appearance appearance) {
    super(common, index, SlotKind.UPGRADE, appearance);
  }

  /** Addon slots have no remembered recipe hint by default; subclasses may supply one. */
  @Override
  public net.minecraft.world.item.ItemStack hint(
      zone.moddev.mc.lib.gui.runtime.ScreenHost host, String inventory) {
    return net.minecraft.world.item.ItemStack.EMPTY;
  }

  @Override
  public MenuSlots.Role role() {
    return MenuSlots.Role.AUXILIARY;
  }
}
