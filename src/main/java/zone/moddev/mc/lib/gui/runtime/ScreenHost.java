package zone.moddev.mc.lib.gui.runtime;

import java.util.Map;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandlerModifiable;

import zone.moddev.mc.lib.api.capabilities.SideConfig;

/** Implement on a block entity to expose its inventory and side groups to a data-defined menu. */
public interface ScreenHost {
  IItemHandlerModifiable screenItems();

  /** Ordered destinations for a player inventory stack; storage still decides what it accepts. */
  default int[] screenDestinations(ItemStack stack) {
    return java.util.stream.IntStream.range(0, screenItems().getSlots()).toArray();
  }

  /** Recipe input slots in recipe order; outputs must not be included. */
  default int[] screenRecipeSlots(net.minecraft.resources.ResourceLocation category) {
    return new int[0];
  }

  default Map<String, SideConfig> screenSides() {
    return Map.of();
  }

  /** Ghost filters are separate from real inventory; return false to reject a change. */
  default boolean setScreenGhost(Player player, int slot, ItemStack filter) {
    return false;
  }

  default ItemStack screenGhost(int slot) {
    return ItemStack.EMPTY;
  }
}
