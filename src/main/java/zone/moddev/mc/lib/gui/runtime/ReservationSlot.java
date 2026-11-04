package zone.moddev.mc.lib.gui.runtime;

import java.util.function.BooleanSupplier;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.SlotItemHandler;

import zone.moddev.mc.lib.api.capabilities.ReservedSlots;

/**
 * Shows the full stack normally, but exposes only its unreserved portion during menu interactions.
 * Vanilla can replace or mutate that portion without ever receiving the reserved items. The same
 * boundary covers pickup, swap, drag and shift-click, rather than filtering individual click types.
 */
class ReservationSlot extends SlotItemHandler {
  private final BooleanSupplier interacting;
  private ItemStack pending;

  ReservationSlot(IItemHandler handler, int index, int x, int y, BooleanSupplier interacting) {
    super(handler, index, x, y);
    this.interacting = interacting;
  }

  private ReservedSlots reservation() {
    return interacting.getAsBoolean() && getItemHandler() instanceof ReservedSlots reserved
        && reserved.hasPlan(index) ? reserved : null;
  }

  private int locked(ReservedSlots reserved) {
    return Math.max(0, super.getItem().getCount() - reserved.freeToTake(index));
  }

  @Override
  public ItemStack getItem() {
    var reserved = reservation();
    if (reserved == null) return super.getItem();
    return pending = super.getItem().copyWithCount(reserved.freeToTake(index));
  }

  @Override
  public int getMaxStackSize(ItemStack stack) {
    if (!(getItemHandler() instanceof ReservedSlots reserved) || !reserved.hasPlan(index))
      return super.getMaxStackSize(stack);
    int limit = Math.min(getMaxStackSize(), reserved.maxHeld(index, stack));
    return Math.max(0, limit - (interacting.getAsBoolean() ? locked(reserved) : 0));
  }

  @Override
  public void set(ItemStack stack) {
    var reserved = reservation();
    var result = stack;
    if (reserved != null) {
      int locked = locked(reserved);
      if (locked > 0) {
        var held = super.getItem();
        if (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(held, stack))
          throw new IllegalArgumentException("Cannot replace reserved items");
        result = held.copyWithCount(locked + stack.getCount());
      }
    }
    pending = null;
    ((IItemHandlerModifiable) getItemHandler()).setStackInSlot(index, result);
    setChanged();
  }

  @Override
  public void setChanged() {
    if (pending != null && reservation() != null) {
      var changed = pending;
      pending = null;
      set(changed);
    } else super.setChanged();
  }

  @Override
  public ItemStack remove(int amount) {
    pending = null;
    return super.remove(amount);
  }

  void endInteraction() {
    pending = null;
  }
}
