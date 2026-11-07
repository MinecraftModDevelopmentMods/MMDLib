package zone.moddev.mc.lib.api.capabilities;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
public class ItemStorage extends PlannedItemHandler {
  private final Role[] roles;
  private final int[] limits;

  @SuppressWarnings("unchecked")
  private final Predicate<ItemStack>[] filters;

  private final Predicate<ItemStack>[] insertionRules;

  private Runnable onChange = () -> {};
  private boolean bypass;
  private long revision;
  private final String[] slotIds;
  private final ListTag recovery = new ListTag();

  /** Optional stable identity for save migration; legacy saves still load by index. */
  public ItemStorage slotId(int slot, String id) {
    checked(slot);
    if (id == null || id.isBlank()) throw new IllegalArgumentException("Empty slot ID");
    for (int i = 0; i < slotIds.length; i++)
      if (i != slot && id.equals(slotIds[i]))
        throw new IllegalArgumentException("Duplicate slot ID: " + id);
    slotIds[slot] = id;
    return this;
  }

  /** Adds an acceptance rule without replacing the machine's existing filter. */
  public ItemStorage restrict(Predicate<ItemStack> filter, int... slots) {
    for (int slot : slots) insertionRules[checked(slot)] = insertionRules[slot].and(filter);
    revision++;
    return this;
  }

  /** Unchecked restoration, explicitly bypassing placement rules and reservations. */
  public void restoreStack(int slot, ItemStack stack) {
    setStackInSlot(slot, stack.copy());
  }

  /** Displaced or undecodable stacks, retained verbatim until recovered or loadable again. */
  public ListTag recoveryContents() {
    return recovery.copy();
  }

  public void recover(Consumer<CompoundTag> receiver) {
    StorageRecovery.handoff(recovery, "mmdlib:items", "item", receiver);
    markChanged();
  }

  /** Explicitly discards contents, displaced items and plans. Stop owning processors first. */
  public void clear() {
    recovery.clear();
    for (int slot = 0; slot < getSlots(); slot++) {
      setPlan(slot, zone.moddev.mc.lib.machine.inventory.SlotPlan.NONE);
      stacks.set(slot, ItemStack.EMPTY);
    }
    markChanged();
  }

  @Override
  public CompoundTag serializeNBT(HolderLookup.Provider provider) {
    var tag = super.serializeNBT(provider);
    for (var value : tag.getList("Items", Tag.TAG_COMPOUND)) {
      var entry = (CompoundTag) value;
      int slot = entry.getInt("Slot");
      if (inRange(slot) && slotIds[slot] != null) entry.putString("SlotId", slotIds[slot]);
    }
    tag.put("Recovery", recovery.copy());
    return tag;
  }

  @Override
  public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
    deserializeNBT(provider, tag, java.util.function.IntUnaryOperator.identity());
  }

  /** Explicit index migration for pre-identity saves. The host selects this by its save version. */
  public void deserializeNBT(
      HolderLookup.Provider provider,
      CompoundTag tag,
      java.util.function.IntUnaryOperator legacySlots) {
    var entries = tag.getList("Items", Tag.TAG_COMPOUND).copy();
    entries.addAll(tag.getList("Recovery", Tag.TAG_COMPOUND).copy());
    recovery.clear();
    for (int i = 0; i < getSlots(); i++) stacks.set(i, ItemStack.EMPTY);
    for (var value : entries) {
      var entry = (CompoundTag) value;
      int slot = entry.getInt("Slot");
      if (entry.contains("SlotId")) {
        slot = -1;
        for (int i = 0; i < slotIds.length; i++)
          if (entry.getString("SlotId").equals(slotIds[i])) {
            slot = i;
            break;
          }
      } else {
        slot = legacySlots.applyAsInt(slot);
        entry.putInt("Slot", slot);
        if (inRange(slot) && slotIds[slot] != null) entry.putString("SlotId", slotIds[slot]);
      }
      var stack = ItemStack.parseOptional(provider, entry);
      if (stack.isEmpty()
          || !inRange(slot)
          || (!stacks.get(slot).isEmpty()
              && !ItemStack.isSameItemSameComponents(stacks.get(slot), stack))) {
        if (!entry.isEmpty()) recovery.add(entry.copy());
        continue;
      }
      int held = stacks.get(slot).getCount();
      int accepted =
          Math.min(
              stack.getCount(),
              Math.max(0, Math.min(getSlotLimit(slot), stack.getMaxStackSize()) - held));
      stacks.set(slot, stack.copyWithCount(held + accepted));
      if (accepted < stack.getCount()) {
        var extra = entry.copy();
        extra.putInt("count", stack.getCount() - accepted);
        recovery.add(extra);
      }
    }
    onLoad();
    markChanged();
  }

  /** Change counter for recipe search invalidation. */
  public long revision() {
    return revision + reservationPlans().revision();
  }

  /** Reports an in-place change made by a menu, including persistence and recipe invalidation. */
  public void markChanged() {
    revision++;
    onChange.run();
  }

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
    insertionRules = new Predicate[size];
    slotIds = new String[size];
    Arrays.fill(roles, Role.STORAGE);
    Arrays.fill(limits, Item.ABSOLUTE_MAX_STACK_SIZE);
    Arrays.fill(filters, (Predicate<ItemStack>) stack -> true);
    Arrays.fill(insertionRules, (Predicate<ItemStack>) stack -> true);
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
    revision++;
    for (int slot : slots) roles[checked(slot)] = role;
    return this;
  }

  /** Only stacks passing {@code filter} may be inserted into these slots, by anyone. */
  public ItemStorage filter(Predicate<ItemStack> filter, int... slots) {
    revision++;
    for (int slot : slots) filters[checked(slot)] = filter;
    return this;
  }

  /** At most {@code limit} items in each of these slots (and never more than a full stack). */
  public ItemStorage limit(int limit, int... slots) {
    revision++;
    for (int slot : slots) limits[checked(slot)] = limit;
    return this;
  }

  /** The role of slot {@code slot}. */
  public Role role(int slot) {
    return roles[checked(slot)];
  }

  /**
   * Inserts the machine's own product, ignoring roles and filters; plans still apply. Returns what
   * did not fit.
   */
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
   * cannot desynchronize the per-slot settings; displaced stacks are retained in recovery.
   */
  @Override
  public void setSize(int size) {
    super.setSize(roles == null ? size : roles.length);
  }

  @Override
  public boolean isItemValid(int slot, ItemStack stack) {
    boolean allowed =
        bypass
            || (roles[slot] != Role.OUTPUT
                && filters[slot].test(stack)
                && insertionRules[slot].test(stack));
    return allowed && super.isItemValid(slot, stack);
  }

  @Override
  public int getSlotLimit(int slot) {
    return limits[slot];
  }

  @Override
  protected void onContentsChanged(int slot) {
    markChanged();
  }

  /** Drops every stack at {@code pos} and empties the storage, e.g. when the block is broken. */
  public void dropAll(Level level, BlockPos pos) {
    for (int slot = 0; slot < getSlots(); slot++) {
      Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), getStackInSlot(slot));
      stacks.set(slot, ItemStack.EMPTY);
    }
    markChanged();
  }

  /** Hands displaced contents to the host before dropping the ordinary slots. */
  public void dropAll(Level level, BlockPos pos, Consumer<CompoundTag> recoverResources) {
    recover(recoverResources);
    dropAll(level, pos);
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
    return view(slot -> mode.get());
  }

  /** Per-slot permissions, preserving every slot index and all storage reservations. */
  public IItemHandler view(IntFunction<IoMode> mode) {
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
        if (!inRange(slot) || !roles[slot].canInsert() || !mode.apply(slot).canInsert())
          return stack;
        return ItemStorage.this.insertItem(slot, stack, simulate);
      }

      @Override
      public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!inRange(slot) || !roles[slot].canExtract() || !mode.apply(slot).canExtract())
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
            && mode.apply(slot).canInsert()
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
