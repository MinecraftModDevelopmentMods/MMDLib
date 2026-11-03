package zone.moddev.mc.lib.machine.inventory;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import zone.moddev.mc.lib.machine.ingredient.FluidAmount;

/** Fluid tanks in a {@link MachineInventory}; amounts in mB. */
public non-sealed interface FluidGroup extends SlotGroup {
  /** The number of tanks. */
  int tanks();

  /** What {@code tank} holds; {@link FluidAmount#EMPTY} outside the group. */
  FluidAmount peek(int tank);

  /** How many mB in {@code tank} no plan takes, so they are free to use; 0 outside the group. */
  int free(int tank);

  /**
   * Drains up to {@code amount} mB from {@code tank}, never more than {@link #free}; returns how
   * much was drained.
   */
  int extract(int tank, int amount, MachineTx tx);

  /**
   * Fills {@code fluid}, into tanks of that fluid first and then empty ones, without breaking any
   * plan; returns mB filled.
   */
  int insert(FluidAmount fluid, MachineTx tx);

  /** Like {@link #insert(FluidAmount, MachineTx)}, but into {@code tank} only. */
  int insert(int tank, FluidAmount fluid, MachineTx tx);

  /**
   * Adds {@code plan} to what is planned for {@code tank}. Returns false and changes nothing if the
   * plans cannot be merged or the result is not legal for what the tank holds. Undone if {@code tx}
   * closes without commit.
   */
  boolean reserve(int tank, TankPlan plan, MachineTx tx);

  /**
   * Adds {@code plan} back to the tank's plan without checking that it is legal, for undoing a
   * {@link #release}.
   *
   * @throws IllegalStateException if the plans cannot be merged
   */
  void restore(int tank, TankPlan plan, MachineTx tx);

  /** Takes {@code plan}, which must have been reserved, out of the tank's plan. */
  void release(int tank, TankPlan plan, MachineTx tx);

  /** Whether all of {@code fluids} would fit together. Changes nothing. */
  default boolean fits(List<FluidAmount> fluids) {
    return fits(fluids, new long[tanks()]);
  }

  /**
   * Whether all of {@code fluids} would fit together once {@code taken[tank]} mB are gone from each
   * tank. Changes nothing.
   */
  default boolean fits(List<FluidAmount> fluids, long[] taken) {
    return place(fluids, taken) != null;
  }

  /**
   * Where {@code fluids} would land once {@code taken[tank]} mB are gone from each tank: per fluid,
   * per tank, how many mB go there. Room is measured against each tank's end state, and a tank
   * whose plan is not legal takes nothing. Null if they do not all fit. Changes nothing.
   */
  @Nullable
  int[][] place(List<FluidAmount> fluids, long[] taken);
}
