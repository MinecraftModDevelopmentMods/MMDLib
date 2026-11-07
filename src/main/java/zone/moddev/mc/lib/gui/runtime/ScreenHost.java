package zone.moddev.mc.lib.gui.runtime;

import java.util.Map;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandlerModifiable;

import zone.moddev.mc.lib.api.capabilities.SideConfig;

/** Implement on a block entity to expose its inventory and side groups to a data-defined menu. */
public interface ScreenHost {
  /**
   * The inventory behind the screen's {@code <slot index>} elements, numbered as in the XML;
   * returning an {@code ItemStorage} keeps its output restrictions and filters for players. It must
   * work on the client too, where the menu asks it what each slot accepts to predict clicks. A
   * custom handler or wrapper with reservations must also implement {@code ReservedSlots},
   * including its plan snapshots, so both menu sides enforce the same reservations.
   */
  IItemHandlerModifiable screenItems();

  /** Registered machine features used by generic processor controls. */
  default zone.moddev.mc.lib.machine.feature.FeatureHost screenFeatures() {
    return null;
  }

  /** Parts contributing statistics, in display order. Providers are only sampled on the server. */
  default java.util.List<? extends zone.moddev.mc.lib.api.stats.StatsProvider> screenStats() {
    return java.util.List.of();
  }

  /** Opt in to the independent redstone face drawer. */
  default zone.moddev.mc.lib.api.redstone.RedstoneControl screenRedstone() {
    return null;
  }

  /** Opt in to coloured resource groups and the interactive inventory drawer. */
  default zone.moddev.mc.lib.api.automation.AutomationInventory screenAutomation() {
    return null;
  }

  /** False exposes only resource selection and side modes, with no addon/filter slots. */
  default boolean screenAutomationAddons() {
    return true;
  }

  /** Ordered destinations for a player inventory stack; storage still decides what it accepts. */
  default int[] screenDestinations(ItemStack stack) {
    return java.util.stream.IntStream.range(0, screenItems().getSlots()).toArray();
  }

  /** Recipe input slots in recipe order; outputs must not be included. */
  default int[] screenRecipeSlots(net.minecraft.resources.ResourceLocation category) {
    return new int[0];
  }

  /**
   * The side configuration of each group, keyed by the element ids in {@code <side-config groups>};
   * a group missing from the map shows no modes and ignores clicks.
   */
  default Map<String, SideConfig> screenSides() {
    return Map.of();
  }

  /** Ghost filters are separate from real inventory; return false to reject a change. */
  default boolean setScreenGhost(Player player, int slot, ItemStack filter) {
    return false;
  }

  /** The authoritative filter shown in ghost slot {@code slot}; empty for none. */
  default ItemStack screenGhost(int slot) {
    return ItemStack.EMPTY;
  }
}
