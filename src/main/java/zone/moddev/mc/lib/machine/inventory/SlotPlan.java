package zone.moddev.mc.lib.machine.inventory;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

/**
 * What a machine means to do to one item slot: take {@code take} of what it holds, then put {@code
 * add} in. Reservations compare against the slot's end state, so items the machine is about to
 * take are free for others and items it is about to add are not.
 */
public record SlotPlan(int take, ItemStack add) {
  /** No plan: the slot behaves as if nothing were reserved. */
  public static final SlotPlan NONE = new SlotPlan(0, ItemStack.EMPTY);

  /**
   * Copies {@code add}.
   *
   * @throws IllegalArgumentException if {@code take} is negative
   */
  public SlotPlan {
    if (take < 0) throw new IllegalArgumentException("Take is negative: " + take);
    add = add.copy();
  }

  /** A copy of the stack to add. */
  @Override
  public ItemStack add() {
    return add.copy();
  }

  /** Whether this takes and adds nothing. */
  public boolean isNone() {
    return take == 0 && add.isEmpty();
  }

  /** How many of {@code held} nobody has planned to take, and so are free to others. */
  public int free(ItemStack held) {
    return Math.max(0, held.getCount() - take);
  }

  /**
   * What {@code held} becomes. Check {@link #legal} first: an {@code add} that cannot stack on the
   * leftover is dropped, and the leftover comes back unchanged.
   */
  public ItemStack endState(ItemStack held) {
    var left = held.copy();
    left.setCount(free(held));
    if (add.isEmpty()) return left;
    if (left.isEmpty()) return add.copy();
    if (!ItemStack.isSameItemSameComponents(left, add)) return left;
    left.grow(add.getCount());
    return left;
  }

  /** Whether the plan can run on {@code held}: enough to take, one stack, within the limit. */
  public boolean legal(ItemStack held, int slotLimit) {
    if (take > held.getCount()) return false;
    var left = free(held);
    if (add.isEmpty()) return true;
    if (left > 0 && !ItemStack.isSameItemSameComponents(held, add)) return false;
    return left + add.getCount() <= Math.min(slotLimit, add.getMaxStackSize());
  }

  /** Whether {@code stack} is the item the slot holds now and will hold after the plan. */
  public boolean accepts(ItemStack held, ItemStack stack) {
    if (stack.isEmpty()) return false;
    if (!held.isEmpty() && !ItemStack.isSameItemSameComponents(held, stack)) return false;
    var end = endState(held);
    return end.isEmpty() || ItemStack.isSameItemSameComponents(end, stack);
  }

  /** How many of {@code stack} may go in now without breaking the plan; 0 unless accepted. */
  public int room(ItemStack held, ItemStack stack, int slotLimit) {
    if (!accepts(held, stack)) return 0;
    var limit = Math.min(slotLimit, stack.getMaxStackSize());
    return Math.max(0, Math.min(limit - endState(held).getCount(), limit - held.getCount()));
  }

  /** This plan and {@code other} together, or null if they add different items. */
  @Nullable
  public SlotPlan plus(SlotPlan other) {
    if (!add.isEmpty()
        && !other.add.isEmpty()
        && !ItemStack.isSameItemSameComponents(add, other.add)) {
      return null;
    }
    var sum = add.isEmpty() ? other.add.copy() : add.copy();
    sum.setCount(add.getCount() + other.add.getCount());
    return new SlotPlan(take + other.take, sum);
  }

  /** This plan without {@code other}, which must have been added with {@link #plus}. */
  public SlotPlan minus(SlotPlan other) {
    var rest = add.copy();
    rest.setCount(Math.max(0, add.getCount() - other.add.getCount()));
    return new SlotPlan(Math.max(0, take - other.take), rest);
  }

  /** Whether {@code other} plans the same thing; records compare stacks by identity. */
  public boolean sameAs(SlotPlan other) {
    return take == other.take && ItemStack.matches(add, other.add);
  }
}
