package zone.moddev.mc.lib.machine.neoforge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import org.jetbrains.annotations.Nullable;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.ItemStore;
import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.lib.machine.inventory.EnergyGroup;
import zone.moddev.mc.lib.machine.inventory.FluidGroup;
import zone.moddev.mc.lib.machine.inventory.ItemGroup;
import zone.moddev.mc.lib.machine.inventory.MachineTx;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;
import zone.moddev.mc.lib.machine.inventory.TankPlan;

/**
 * Machine groups over MMDLib's storages. They work as the block entity does:
 * items through {@link ItemStore#insertResult} and the storage's own extract
 * (so slot roles and filters don't apply), fluids through
 * {@link FluidStorage#tank}, energy ignoring the buffer's transfer rates but
 * not its capacity. Storages keep their plans, so extract and insert stop short
 * of what is reserved. Transactions copy a slot, tank or buffer the first time
 * they change it, and a slot's or tank's plan separately from its contents.
 */
public final class StorageGroups {
    private StorageGroups() {
    }

    /** Slots {@code slots} of {@code storage}, as group slots 0, 1, ... */
    public static ItemGroup items(ItemStore storage, int... slots) {
        for (int slot : slots)
            if (slot < 0 || slot >= storage.getSlots())
                throw new IndexOutOfBoundsException("No slot " + slot
                    + " in a storage of " + storage.getSlots());
        return new ItemSlots(storage, slots.clone());
    }

    /** Tanks {@code tanks} of {@code storage}, as group tanks 0, 1, ... */
    public static FluidGroup fluids(FluidStorage storage, int... tanks) {
        for (int tank : tanks)
            if (tank < 0 || tank >= storage.getTanks())
                throw new IndexOutOfBoundsException("No tank " + tank
                    + " in a storage of " + storage.getTanks());
        return new FluidTanks(storage, tanks.clone());
    }

    /** All of {@code buffer}. */
    public static EnergyGroup energy(EnergyBuffer buffer) {
        return new EnergyStore(buffer);
    }

    /**
     * A transaction key for the contents of one slot or tank; storages compare
     * by identity.
     */
    private record Key(Object storage, int index) {
    }

    private record ItemSlots(ItemStore storage, int[] slots)
        implements ItemGroup {
        @Override
        public long revision() {
            return storage.revision();
        }

        @Override
        public int size() {
            return slots.length;
        }

        @Override
        public ItemStack peek(int slot) {
            return valid(slot)
                ? storage.getStackInSlot(slots[slot]).copy()
                : ItemStack.EMPTY;
        }

        @Override
        public int free(int slot) {
            return valid(slot) ? storage.freeToTake(slots[slot]) : 0;
        }

        @Override
        public int extract(int slot, int count, MachineTx tx) {
            if (!valid(slot) || count <= 0)
                return 0;
            int index = slots[slot];
            if (storage.getStackInSlot(index).isEmpty())
                return 0;
            track(index, tx);
            return storage.extractItem(index, count, false).getCount();
        }

        @Override
        public int insert(ItemStack stack, MachineTx tx) {
            var remaining = stack.copy();
            for (boolean merging : new boolean[]{true, false})
                for (int slot = 0; slot < slots.length; slot++) {
                    if (remaining.isEmpty())
                        break;
                    if (merging == storage.getStackInSlot(slots[slot])
                        .isEmpty())
                        continue;
                    remaining.shrink(insert(slot, remaining.copy(), tx));
                }
            return stack.getCount() - remaining.getCount();
        }

        @Override
        public int insert(int slot, ItemStack stack, MachineTx tx) {
            if (!valid(slot) || stack.isEmpty())
                return 0;
            int index = slots[slot];
            if (storage.insertResult(index, stack, true).getCount() == stack
                .getCount())
                return 0;
            track(index, tx);
            return stack.getCount()
                - storage.insertResult(index, stack.copy(), false).getCount();
        }

        @Override
        public boolean reserve(int slot, SlotPlan plan, MachineTx tx) {
            if (!valid(slot))
                return false;
            return storage.storage(slots[slot]).reservationPlans()
                .reserve(storage.localSlot(slots[slot]), plan, tx);
        }

        @Override
        public boolean restore(int slot, SlotPlan plan, MachineTx tx) {
            if (!valid(slot))
                return false;
            return storage.storage(slots[slot]).reservationPlans()
                .restore(storage.localSlot(slots[slot]), plan, tx);
        }

        @Override
        public void release(int slot, SlotPlan plan, MachineTx tx) {
            if (!valid(slot))
                return;
            storage.storage(slots[slot]).reservationPlans()
                .release(storage.localSlot(slots[slot]), plan, tx);
        }

        @Override
        @Nullable
        public int[][] place(List<ItemStack> stacks, long[] taken) {
            var held = new ArrayList<ItemStack>();
            var usable = new boolean[slots.length];
            for (int i = 0; i < slots.length; i++) {
                var stack = storage.getStackInSlot(slots[i]);
                var plan = storage.plan(slots[i]);
                var asked = new SlotPlan(plan.take() + (int) taken[i],
                    plan.add());
                usable[i] = asked.legal(stack, storage.getSlotLimit(slots[i]));
                int after = stack.getCount() - (int) taken[i];
                held.add(
                    usable[i] ? stack.copyWithCount(after) : ItemStack.EMPTY);
            }
            var placed = new int[stacks.size()][slots.length];
            for (int n = 0; n < stacks.size(); n++) {
                var stack = stacks.get(n);
                int left = stack.getCount();
                for (boolean merging : new boolean[]{true, false})
                    for (int i = 0; i < slots.length && left > 0; i++) {
                        var slot = held.get(i);
                        var plan = storage.plan(slots[i]);
                        if (!usable[i]
                            || merging == plan.endState(slot).isEmpty())
                            continue;
                        int moved = Math.min(left, plan.room(slot, stack,
                            storage.getSlotLimit(slots[i])));
                        if (moved <= 0)
                            continue;
                        held.set(i,
                            stack.copyWithCount(slot.getCount() + moved));
                        placed[n][i] = moved;
                        left -= moved;
                    }
                if (left > 0)
                    return null;
            }
            return placed;
        }

        private boolean valid(int slot) {
            return slot >= 0 && slot < slots.length;
        }

        private void track(int index, MachineTx tx) {
            tx.track(new Key(storage.storage(index), storage.localSlot(index)),
                () -> {
                    var before = storage.getStackInSlot(index).copy();
                    return () -> storage.restoreStack(index, before);
                });
        }
    }

    private record FluidTanks(FluidStorage storage, int[] indexes)
        implements FluidGroup {
        @Override
        public long revision() {
            return storage.revision();
        }

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
        public int free(int tank) {
            return valid(tank)
                ? storage.plan(indexes[tank]).free(peek(tank))
                : 0;
        }

        @Override
        public int extract(int tank, int amount, MachineTx tx) {
            if (!valid(tank) || amount <= 0)
                return 0;
            var handler = storage.tank(indexes[tank]);
            if (handler.isEmpty())
                return 0;
            track(indexes[tank], tx);
            return handler.drain(amount, FluidAction.EXECUTE).getAmount();
        }

        @Override
        public int insert(FluidAmount fluid, MachineTx tx) {
            if (fluid.isEmpty())
                return 0;
            var remaining = fluid;
            for (boolean merging : new boolean[]{true, false})
                for (int tank = 0; tank < indexes.length; tank++) {
                    if (remaining.amount() == 0)
                        break;
                    if (merging == storage.tank(indexes[tank]).isEmpty())
                        continue;
                    remaining = remaining.withAmount(
                        remaining.amount() - insert(tank, remaining, tx));
                }
            return fluid.amount() - remaining.amount();
        }

        @Override
        public int insert(int tank, FluidAmount fluid, MachineTx tx) {
            if (!valid(tank) || fluid.isEmpty())
                return 0;
            var stack = FluidStacks.stack(fluid);
            if (storage.fillResult(indexes[tank], stack,
                FluidAction.SIMULATE) <= 0)
                return 0;
            track(indexes[tank], tx);
            return storage.fillResult(indexes[tank], stack,
                FluidAction.EXECUTE);
        }

        @Override
        public boolean reserve(int tank, TankPlan plan, MachineTx tx) {
            if (!valid(tank))
                return false;
            return storage.reservationPlans().reserve(indexes[tank], plan, tx);
        }

        @Override
        public boolean restore(int tank, TankPlan plan, MachineTx tx) {
            if (!valid(tank))
                return false;
            return storage.reservationPlans().restore(indexes[tank], plan, tx);
        }

        @Override
        public void release(int tank, TankPlan plan, MachineTx tx) {
            if (!valid(tank))
                return;
            storage.reservationPlans().release(indexes[tank], plan, tx);
        }

        @Override
        @Nullable
        public int[][] place(List<FluidAmount> fluids, long[] taken) {
            var held = new ArrayList<FluidAmount>();
            var usable = new boolean[indexes.length];
            for (int i = 0; i < indexes.length; i++) {
                var tank = storage.tank(indexes[i]);
                var amount = FluidStacks.amount(tank.getFluid());
                var plan = storage.plan(indexes[i]);
                var asked = new TankPlan(plan.take() + (int) taken[i],
                    plan.add());
                usable[i] = asked.legal(amount, tank.getCapacity());
                int after = amount.amount() - (int) taken[i];
                held.add(
                    usable[i] ? amount.withAmount(after) : FluidAmount.EMPTY);
            }
            var placed = new int[fluids.size()][indexes.length];
            for (int n = 0; n < fluids.size(); n++) {
                var fluid = fluids.get(n);
                if (fluid.isEmpty())
                    continue;
                var stack = FluidStacks.stack(fluid);
                int left = fluid.amount();
                for (boolean merging : new boolean[]{true, false})
                    for (int i = 0; i < indexes.length && left > 0; i++) {
                        var tank = held.get(i);
                        var plan = storage.plan(indexes[i]);
                        if (!usable[i]
                            || merging == plan.endState(tank).isEmpty())
                            continue;
                        if (!merging
                            && !storage.tank(indexes[i]).isFluidValid(stack))
                            continue;
                        int moved = Math.min(left, plan.room(tank, fluid,
                            storage.tank(indexes[i]).getCapacity()));
                        if (moved <= 0)
                            continue;
                        held.set(i, fluid.withAmount(tank.amount() + moved));
                        placed[n][i] = moved;
                        left -= moved;
                    }
                if (left > 0)
                    return null;
            }
            return placed;
        }

        private boolean valid(int tank) {
            return tank >= 0 && tank < indexes.length;
        }

        private void track(int index, MachineTx tx) {
            tx.track(new Key(storage, index), () -> {
                var before = storage.tank(index).getFluid().copy();
                return () -> storage.restoreFluid(index, before);
            });
        }
    }

    private record EnergyStore(EnergyBuffer buffer) implements EnergyGroup {
        @Override
        public long revision() {
            // Stored FE is the energy group's only recipe-matching input.
            // Returning to the same
            // amount leaves a failed match unchanged; a refill must bypass the
            // idle retry delay.
            return buffer.stored();
        }

        @Override
        public Object statisticsResource() {
            return buffer;
        }

        @Override
        public long consume(long amount, MachineTx tx) {
            if (amount <= 0 || amount > buffer.stored())
                return 0;
            track(tx);
            return buffer.consume(amount) ? amount : 0;
        }

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
            if (buffer.extract(amount, true) <= 0)
                return 0;
            track(tx);
            return buffer.extract(amount, false);
        }

        @Override
        public long insert(long amount, MachineTx tx) {
            if (buffer.insert(amount, true) <= 0)
                return 0;
            track(tx);
            return buffer.insert(amount, false);
        }

        private void track(MachineTx tx) {
            tx.track(buffer, buffer::checkpoint);
        }
    }
}
