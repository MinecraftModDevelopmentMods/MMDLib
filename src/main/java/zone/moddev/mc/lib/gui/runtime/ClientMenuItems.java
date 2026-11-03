package zone.moddev.mc.lib.gui.runtime;

import java.util.Arrays;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import zone.moddev.mc.lib.api.capabilities.ReservedSlots;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/**
 * The client's copy of the slots behind a {@link MachineMenu}. Vanilla's menu sync fills it; what
 * each slot accepts and how much it holds come from the client block entity's own {@link
 * ScreenHost#screenItems()}, so a click is predicted the way the server will answer it. The server
 * still decides: a filter that depends on server-only state can mispredict, and the server's answer
 * replaces the prediction.
 *
 * <p>Reservations arrive as each slot's {@link SlotPlan} and apply the same way they do in the
 * server's storage.
 */
public final class ClientMenuItems extends ItemStackHandler implements ReservedSlots {
  private final IItemHandler rules;
  private final SlotPlan[] plans;

  /**
   * @param size the menu's slot count, which ghost slots can take past the end of {@code rules}
   * @param rules asked what each slot accepts; its contents aren't read or changed
   */
  public ClientMenuItems(int size, IItemHandler rules) {
    super(size);
    this.rules = rules;
    plans = new SlotPlan[size];
    Arrays.fill(plans, SlotPlan.NONE);
  }

  /** Replaces what the machine plans for a slot; a slot that does not exist is ignored. */
  public void setPlan(int slot, SlotPlan plan) {
    if (inRange(slot)) plans[slot] = plan;
  }

  @Override
  public boolean hasPlan(int slot) {
    return inRange(slot) && !plans[slot].isNone();
  }

  @Override
  public int freeToTake(int slot) {
    return plans[slot].free(getStackInSlot(slot));
  }

  @Override
  public int maxHeld(int slot, ItemStack stack) {
    if (!hasPlan(slot)) return Math.min(getSlotLimit(slot), stack.getMaxStackSize());
    var held = getStackInSlot(slot);
    return held.getCount() + plans[slot].room(held, stack, getSlotLimit(slot));
  }

  @Override
  public boolean isItemValid(int slot, ItemStack stack) {
    return slot < rules.getSlots()
        && rules.isItemValid(slot, stack)
        && (!hasPlan(slot) || plans[slot].accepts(getStackInSlot(slot), stack));
  }

  @Override
  public ItemStack extractItem(int slot, int amount, boolean simulate) {
    validateSlotIndex(slot);
    int allowed = hasPlan(slot) ? Math.min(amount, freeToTake(slot)) : amount;
    return super.extractItem(slot, allowed, simulate);
  }

  @Override
  protected int getStackLimit(int slot, ItemStack stack) {
    return maxHeld(slot, stack);
  }

  private boolean inRange(int slot) {
    return slot >= 0 && slot < plans.length;
  }

  @Override
  public int getSlotLimit(int slot) {
    return slot < rules.getSlots() ? rules.getSlotLimit(slot) : 0;
  }
}
