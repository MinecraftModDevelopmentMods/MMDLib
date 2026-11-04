package zone.moddev.mc.lib.api.capabilities;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.ItemStackHandler;

import zone.moddev.mc.lib.machine.inventory.ReservationPlans;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/** Reservation enforcement shared by authoritative storages and client menu prediction. */
public class PlannedItemHandler extends ItemStackHandler implements ReservedSlots {
  private final ReservationPlans<SlotPlan> plans;

  public PlannedItemHandler(int size) {
    super(size);
    plans = new ReservationPlans<>(size, SlotPlan.NONE, SlotPlan::plus, SlotPlan::minus,
        (slot, plan) -> plan.legal(getStackInSlot(slot), getSlotLimit(slot)));
  }

  /** The same plan table is used by all groups over this storage, including rollback keys. */
  public final ReservationPlans<SlotPlan> reservationPlans() {
    return plans;
  }

  @Override
  public SlotPlan plan(int slot) {
    return plans.get(slot);
  }

  /** Replaces a plan for synchronization or restoring state. */
  public void setPlan(int slot, SlotPlan plan) {
    plans.set(slot, plan);
  }

  public boolean reserve(int slot, SlotPlan plan) {
    return plans.reserve(slot, plan);
  }

  public void release(int slot, SlotPlan plan) {
    plans.release(slot, plan);
  }

  @Override
  public boolean hasPlan(int slot) {
    return slot >= 0 && slot < getSlots() && !plan(slot).isNone();
  }

  @Override
  public int freeToTake(int slot) {
    return plan(slot).free(getStackInSlot(slot));
  }

  @Override
  public int maxHeld(int slot, ItemStack stack) {
    if (!hasPlan(slot)) return Math.min(getSlotLimit(slot), stack.getMaxStackSize());
    var held = getStackInSlot(slot);
    return held.getCount() + plan(slot).room(held, stack, getSlotLimit(slot));
  }

  @Override
  public boolean isItemValid(int slot, ItemStack stack) {
    return !hasPlan(slot) || plan(slot).accepts(getStackInSlot(slot), stack);
  }

  @Override
  public ItemStack extractItem(int slot, int amount, boolean simulate) {
    validateSlotIndex(slot);
    return super.extractItem(slot, Math.min(amount, freeToTake(slot)), simulate);
  }

  @Override
  protected int getStackLimit(int slot, ItemStack stack) {
    return maxHeld(slot, stack);
  }

  @Override
  public void setSize(int size) {
    // ItemStackHandler calls this from its constructor, before the plan table exists.
    super.setSize(plans == null ? size : getSlots());
  }
}
