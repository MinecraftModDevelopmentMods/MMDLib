package zone.moddev.mc.lib.machine.inventory;

import java.util.List;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/** Item slots in a {@link MachineInventory}. */
public non-sealed interface ItemGroup extends SlotGroup {
    /** The number of slots. */
    int size();

    /** A copy of the stack in {@code slot}; empty outside the group. */
    ItemStack peek(int slot);

    /**
     * How many items in {@code slot} no plan takes, so they are free to use; 0
     * outside the group.
     */
    int free(int slot);

    /**
     * Takes up to {@code count} items from {@code slot}, never more than
     * {@link #free}; returns how many were taken.
     */
    int extract(int slot, int count, MachineTx tx);

    /**
     * Inserts {@code stack}, onto matching stacks first and then into empty
     * slots, without breaking any plan; returns how many fitted. {@code stack}
     * itself is not changed.
     */
    int insert(ItemStack stack, MachineTx tx);

    /**
     * Like {@link #insert(ItemStack, MachineTx)}, but into {@code slot} only.
     */
    int insert(int slot, ItemStack stack, MachineTx tx);

    /**
     * Adds {@code plan} to what is planned for {@code slot}. Returns false and
     * changes nothing if the plans cannot be merged or the result is not legal
     * for what the slot holds. Undone if {@code tx} closes without commit.
     */
    boolean reserve(int slot, SlotPlan plan, MachineTx tx);

    /**
     * Adds {@code plan} to the slot's plan without checking that it is legal,
     * for loading plans that were saved while legal. Returns false and changes
     * nothing if slot is outside the group or the plans cannot be merged.
     */
    boolean restore(int slot, SlotPlan plan, MachineTx tx);

    /**
     * Takes {@code plan}, which must have been reserved, out of the slot's
     * plan.
     */
    void release(int slot, SlotPlan plan, MachineTx tx);

    /**
     * Whether all of {@code stacks} would fit together. Works on copies;
     * changes nothing.
     */
    default boolean fits(List<ItemStack> stacks) {
        return fits(stacks, new long[size()]);
    }

    /**
     * Whether all of {@code stacks} would fit together once {@code taken[slot]}
     * items are gone from each slot. Works on copies; changes nothing.
     */
    default boolean fits(List<ItemStack> stacks, long[] taken) {
        return place(stacks, taken) != null;
    }

    /**
     * Where {@code stacks} would land once {@code taken[slot]} items are gone
     * from each slot: per stack, per slot, how many go there. A slot's end
     * state decides what may join it and how much room planned takes and adds
     * leave, but items another plan has yet to take still fill the slot now,
     * and a different item waits until the slot has physically emptied. A slot
     * whose plan is not legal takes nothing. Null if they do not all fit. Works
     * on copies; changes nothing.
     */
    @Nullable
    int[][] place(List<ItemStack> stacks, long[] taken);
}
