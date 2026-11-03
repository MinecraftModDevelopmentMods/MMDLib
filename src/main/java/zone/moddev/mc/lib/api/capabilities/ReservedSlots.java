package zone.moddev.mc.lib.api.capabilities;

import net.minecraft.world.item.ItemStack;

/** What a menu needs to know about a slot whose contents a machine has made plans for. */
public interface ReservedSlots {
  /** Whether the machine has planned something for this slot; false if it does not exist. */
  boolean hasPlan(int slot);

  /** How many items a player may take from this slot now without breaking the plan. */
  int freeToTake(int slot);

  /** The total this slot may hold of {@code stack}, as {@code Slot.getMaxStackSize(stack)}. */
  int maxHeld(int slot, ItemStack stack);
}
