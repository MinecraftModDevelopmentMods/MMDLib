package zone.moddev.mc.lib.machine.neoforge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.lib.machine.inventory.EnergyGroup;
import zone.moddev.mc.lib.machine.inventory.FluidGroup;
import zone.moddev.mc.lib.machine.inventory.ItemGroup;
import zone.moddev.mc.lib.machine.inventory.MachineTx;

/**
 * Machine groups over MMDLib's storages. They work as the block entity does: items through {@link
 * ItemStorage#insertResult} and the storage's own extract (so slot roles and filters don't apply),
 * fluids through {@link FluidStorage#tank}, energy ignoring the buffer's transfer rates but not its
 * capacity. Transactions copy a slot, tank or buffer the first time they change it.
 */
public final class StorageGroups {
  private StorageGroups() {}

  /** Slots {@code slots} of {@code storage}, as group slots 0, 1, ... */
  public static ItemGroup items(ItemStorage storage, int... slots) {
    for (int slot : slots)
      if (slot < 0 || slot >= storage.getSlots())
        throw new IndexOutOfBoundsException(
            "No slot " + slot + " in a storage of " + storage.getSlots());
    return new ItemSlots(storage, slots.clone());
  }

  /** Tanks {@code tanks} of {@code storage}, as group tanks 0, 1, ... */
  public static FluidGroup fluids(FluidStorage storage, int... tanks) {
    for (int tank : tanks)
      if (tank < 0 || tank >= storage.getTanks())
        throw new IndexOutOfBoundsException(
            "No tank " + tank + " in a storage of " + storage.getTanks());
    return new FluidTanks(storage, tanks.clone());
  }

  /** All of {@code buffer}. */
  public static EnergyGroup energy(EnergyBuffer buffer) {
    return new EnergyStore(buffer);
  }

  /** A transaction key for one slot or tank of one storage; storages compare by identity. */
  private record Key(Object storage, int index) {}

  private record ItemSlots(ItemStorage storage, int[] slots) implements ItemGroup {
    @Override
    public int size() {
      return slots.length;
    }

    @Override
    public ItemStack peek(int slot) {
      return valid(slot) ? storage.getStackInSlot(slots[slot]).copy() : ItemStack.EMPTY;
    }

    @Override
    public int extract(int slot, int count, MachineTx tx) {
      if (!valid(slot) || count <= 0) return 0;
      int index = slots[slot];
      if (storage.getStackInSlot(index).isEmpty()) return 0;
      track(index, tx);
      return storage.extractItem(index, count, false).getCount();
    }

    @Override
    public int insert(ItemStack stack, MachineTx tx) {
      var remaining = stack.copy();
      for (boolean merging : new boolean[] {true, false})
        for (int index : slots) {
          if (remaining.isEmpty()) break;
          var held = storage.getStackInSlot(index);
          if (merging == held.isEmpty()) continue;
          int fits = remaining.getCount() - storage.insertResult(index, remaining, true).getCount();
          if (fits <= 0) continue;
          track(index, tx);
          remaining = storage.insertResult(index, remaining, false);
        }
      return stack.getCount() - remaining.getCount();
    }

    @Override
    public boolean fits(List<ItemStack> stacks, long[] taken) {
      var held = new ArrayList<ItemStack>();
      for (int i = 0; i < slots.length; i++) {
        var stack = storage.getStackInSlot(slots[i]);
        held.add(stack.copyWithCount(stack.getCount() - (int) taken[i]));
      }
      for (var stack : stacks) {
        int left = stack.getCount();
        for (boolean merging : new boolean[] {true, false})
          for (int i = 0; i < slots.length && left > 0; i++) {
            var slot = held.get(i);
            if (merging == slot.isEmpty()) continue;
            if (merging && !ItemStack.isSameItemSameComponents(slot, stack)) continue;
            int limit = Math.min(storage.getSlotLimit(slots[i]), stack.getMaxStackSize());
            int moved = Math.min(left, limit - slot.getCount());
            if (moved <= 0) continue;
            if (slot.isEmpty()) held.set(i, stack.copyWithCount(moved));
            else slot.grow(moved);
            left -= moved;
          }
        if (left > 0) return false;
      }
      return true;
    }

    private boolean valid(int slot) {
      return slot >= 0 && slot < slots.length;
    }

    private void track(int index, MachineTx tx) {
      tx.track(
          new Key(storage, index),
          () -> {
            var before = storage.getStackInSlot(index).copy();
            return () -> storage.setStackInSlot(index, before);
          });
    }
  }

  private record FluidTanks(FluidStorage storage, int[] indexes) implements FluidGroup {
    @Override
    public int tanks() {
      return indexes.length;
    }

    @Override
    public FluidAmount peek(int tank) {
      return valid(tank)
          ? FluidStacks.amount(storage.tank(indexes[tank]).getFluid())
          : FluidAmount.EMPTY;
    }

    @Override
    public int extract(int tank, int amount, MachineTx tx) {
      if (!valid(tank) || amount <= 0) return 0;
      var handler = storage.tank(indexes[tank]);
      if (handler.isEmpty()) return 0;
      track(indexes[tank], tx);
      return handler.drain(amount, FluidAction.EXECUTE).getAmount();
    }

    @Override
    public int insert(FluidAmount fluid, MachineTx tx) {
      if (fluid.isEmpty()) return 0;
      var remaining = FluidStacks.stack(fluid);
      for (boolean merging : new boolean[] {true, false})
        for (int index : indexes) {
          if (remaining.isEmpty()) break;
          var handler = storage.tank(index);
          if (merging == handler.isEmpty()) continue;
          if (handler.fill(remaining, FluidAction.SIMULATE) <= 0) continue;
          track(index, tx);
          remaining.shrink(handler.fill(remaining, FluidAction.EXECUTE));
        }
      return fluid.amount() - remaining.getAmount();
    }

    @Override
    public boolean fits(List<FluidAmount> fluids, long[] taken) {
      var held = new ArrayList<FluidStack>();
      for (int i = 0; i < indexes.length; i++) {
        var fluid = storage.tank(indexes[i]).getFluid();
        held.add(fluid.copyWithAmount(fluid.getAmount() - (int) taken[i]));
      }
      for (var fluid : fluids) {
        if (fluid.isEmpty()) continue;
        var stack = FluidStacks.stack(fluid);
        int left = fluid.amount();
        for (boolean merging : new boolean[] {true, false})
          for (int i = 0; i < indexes.length && left > 0; i++) {
            var tank = held.get(i);
            if (merging == tank.isEmpty()) continue;
            if (merging && !FluidStack.isSameFluidSameComponents(tank, stack)) continue;
            if (!merging && !storage.tank(indexes[i]).isFluidValid(stack)) continue;
            int moved = Math.min(left, storage.tank(indexes[i]).getCapacity() - tank.getAmount());
            if (moved <= 0) continue;
            if (tank.isEmpty()) held.set(i, stack.copyWithAmount(moved));
            else tank.grow(moved);
            left -= moved;
          }
        if (left > 0) return false;
      }
      return true;
    }

    private boolean valid(int tank) {
      return tank >= 0 && tank < indexes.length;
    }

    private void track(int index, MachineTx tx) {
      tx.track(
          new Key(storage, index),
          () -> {
            var before = storage.tank(index).getFluid().copy();
            return () -> storage.tank(index).setFluid(before);
          });
    }
  }

  private record EnergyStore(EnergyBuffer buffer) implements EnergyGroup {
    @Override
    public long stored() {
      return buffer.stored();
    }

    @Override
    public long capacity() {
      return buffer.capacity();
    }

    @Override
    public long extract(long amount, MachineTx tx) {
      if (buffer.extract(amount, true) <= 0) return 0;
      track(tx);
      return buffer.extract(amount, false);
    }

    @Override
    public long insert(long amount, MachineTx tx) {
      if (buffer.insert(amount, true) <= 0) return 0;
      track(tx);
      return buffer.insert(amount, false);
    }

    private void track(MachineTx tx) {
      tx.track(
          buffer,
          () -> {
            long before = buffer.stored();
            return () -> buffer.setStored(before);
          });
    }
  }
}
