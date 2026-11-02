package zone.moddev.mc.lib.machine.inventory;

import java.util.List;

import zone.moddev.mc.lib.machine.ingredient.FluidAmount;

/** Fluid tanks in a {@link MachineInventory}; amounts in mB. */
public non-sealed interface FluidGroup extends SlotGroup {
  /** The number of tanks. */
  int tanks();

  /** What {@code tank} holds; {@link FluidAmount#EMPTY} outside the group. */
  FluidAmount peek(int tank);

  /** Drains up to {@code amount} mB from {@code tank}; returns how much was drained. */
  int extract(int tank, int amount, MachineTx tx);

  /** Fills {@code fluid}, into tanks of that fluid first and then empty ones; returns mB filled. */
  int insert(FluidAmount fluid, MachineTx tx);

  /** Whether all of {@code fluids} would fit together. Changes nothing. */
  default boolean fits(List<FluidAmount> fluids) {
    return fits(fluids, new long[tanks()]);
  }

  /**
   * Whether all of {@code fluids} would fit together once {@code taken[tank]} mB are gone from each
   * tank. Changes nothing.
   */
  boolean fits(List<FluidAmount> fluids, long[] taken);
}
