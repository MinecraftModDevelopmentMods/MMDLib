package zone.moddev.mc.lib.api.capabilities;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A machine inventory: an {@link ItemStackHandler} with a {@link Role}, filter and limit per slot,
 * and a change callback. The owning block entity and its screen use it directly; automation gets a
 * {@link #view} that also applies roles and the face's {@link IoMode}.
 *
 * <p>Directly, a slot's filter decides what may be placed in it and {@link Role#OUTPUT} slots
 * refuse placement, so players cannot put items into outputs. The machine fills outputs with {@link
 * #insertResult}.
 */
public class ItemStorage extends ItemStackHandler {
  private final Role[] roles;
  private final int[] limits;

  @SuppressWarnings("unchecked")
  private final Predicate<ItemStack>[] filters;

  private Runnable onChange = () -> {};
  private boolean bypass;

  /**
   * An empty inventory of {@code size} slots, all {@link Role#STORAGE} with no filter and a
   * full-stack limit.
   */
  @SuppressWarnings("unchecked")
  public ItemStorage(int size) {
    super(size);
    roles = new Role[size];
    limits = new int[size];
    filters = new Predicate[size];
    Arrays.fill(roles, Role.STORAGE);
    Arrays.fill(limits, Item.ABSOLUTE_MAX_STACK_SIZE);
    Arrays.fill(filters, (Predicate<ItemStack>) stack -> true);
  }

  /** Runs after every change, typically the block entity's {@code setChanged}. */
  public ItemStorage onChange(Runnable onChange) {
    this.onChange = onChange;
    return this;
  }

  /**
   * Sets the role of these slots.
   *
   * @throws IndexOutOfBoundsException if a slot does not exist
   */
  public ItemStorage role(Role role, int... slots) {
    for (int slot : slots) roles[checked(slot)] = role;
    return this;
  }

  /** Only stacks passing {@code filter} may be inserted into these slots, by anyone. */
  public ItemStorage filter(Predicate<ItemStack> filter, int... slots) {
    for (int slot : slots) filters[checked(slot)] = filter;
    return this;
  }

  /** At most {@code limit} items in each of these slots (and never more than a full stack). */
  public ItemStorage limit(int limit, int... slots) {
    for (int slot : slots) limits[checked(slot)] = limit;
    return this;
  }

  /** The role of slot {@code slot}. */
  public Role role(int slot) {
    return roles[checked(slot)];
  }

  /** Inserts the machine's own product, ignoring roles and filters. Returns what did not fit. */
  public ItemStack insertResult(int slot, ItemStack stack, boolean simulate) {
    bypass = true;
    try {
      return insertItem(slot, stack, simulate);
    } finally {
      bypass = false;
    }
  }

  /** Whether {@link #insertResult} would accept all of {@code stack}. */
  public boolean fits(int slot, ItemStack stack) {
    return insertResult(slot, stack, true).isEmpty();
  }

  /**
   * Keeps the size given to the constructor, so loading a save written with a different slot count
   * cannot desynchronize the per-slot settings; stacks in slots that no longer exist are dropped.
   */
  @Override
  public void setSize(int size) {
    super.setSize(roles == null ? size : roles.length);
  }

  @Override
  public boolean isItemValid(int slot, ItemStack stack) {
    return bypass || (roles[slot] != Role.OUTPUT && filters[slot].test(stack));
  }

  @Override
  public int getSlotLimit(int slot) {
    return limits[slot];
  }

  @Override
  protected void onContentsChanged(int slot) {
    onChange.run();
  }

  /** Drops every stack at {@code pos} and empties the storage, e.g. when the block is broken. */
  public void dropAll(Level level, BlockPos pos) {
    for (int slot = 0; slot < getSlots(); slot++) {
      Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), getStackInSlot(slot));
      stacks.set(slot, ItemStack.EMPTY);
    }
    onChange.run();
  }

  /** An automation view with a fixed mode, e.g. {@link IoMode#BOTH} for unsided access. */
  public IItemHandler view(IoMode mode) {
    return view(() -> mode);
  }

  /**
   * An automation view whose mode is read on every call, so it follows side-configuration changes
   * and rotation without invalidating capabilities. Every view shows all slots with the same
   * numbering; a slot index out of range is refused rather than thrown.
   */
  public IItemHandler view(Supplier<IoMode> mode) {
    return new IItemHandler() {
      @Override
      public int getSlots() {
        return ItemStorage.this.getSlots();
      }

      @Override
      public ItemStack getStackInSlot(int slot) {
        return inRange(slot) ? ItemStorage.this.getStackInSlot(slot) : ItemStack.EMPTY;
      }

      @Override
      public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!inRange(slot) || !roles[slot].canInsert() || !mode.get().canInsert()) return stack;
        return ItemStorage.this.insertItem(slot, stack, simulate);
      }

      @Override
      public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!inRange(slot) || !roles[slot].canExtract() || !mode.get().canExtract())
          return ItemStack.EMPTY;
        return ItemStorage.this.extractItem(slot, amount, simulate);
      }

      @Override
      public int getSlotLimit(int slot) {
        return inRange(slot) ? ItemStorage.this.getSlotLimit(slot) : 0;
      }

      @Override
      public boolean isItemValid(int slot, ItemStack stack) {
        return inRange(slot)
            && roles[slot].canInsert()
            && mode.get().canInsert()
            && ItemStorage.this.isItemValid(slot, stack);
      }
    };
  }

  private boolean inRange(int slot) {
    return slot >= 0 && slot < getSlots();
  }

  private int checked(int slot) {
    if (!inRange(slot))
      throw new IndexOutOfBoundsException("Slot " + slot + " not in [0," + getSlots() + ")");
    return slot;
  }
}
