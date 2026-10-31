package zone.moddev.mc.lib.machine.inventory;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/** Item slots in a {@link MachineInventory}. */
public non-sealed interface ItemGroup extends SlotGroup {
  /** The number of slots. */
  int size();

  /** A copy of the stack in {@code slot}; empty outside the group. */
  ItemStack peek(int slot);

  /** Takes up to {@code count} items from {@code slot}; returns how many were taken. */
  int extract(int slot, int count, MachineTx tx);

  /**
   * Inserts {@code stack}, onto matching stacks first and then into empty slots; returns how many
   * fitted. {@code stack} itself is not changed.
   */
  int insert(ItemStack stack, MachineTx tx);

  /** Whether all of {@code stacks} would fit together. Works on copies; changes nothing. */
  boolean fits(List<ItemStack> stacks);
}
