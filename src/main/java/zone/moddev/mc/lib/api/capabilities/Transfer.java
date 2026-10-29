package zone.moddev.mc.lib.api.capabilities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Moving items, fluids and energy between handlers, and finding a neighbour's handler. For a
 * neighbour checked every tick, keep a NeoForge {@code BlockCapabilityCache} instead of calling
 * {@link #neighbour} each time.
 */
public final class Transfer {
  private Transfer() {}

  /**
   * The capability of the block next to {@code pos} on side {@code side}, asked for on the face
   * that touches {@code pos}; null when there is none.
   */
  public static <T> T neighbour(
      BlockCapability<T, Direction> capability, Level level, BlockPos pos, Direction side) {
    return level.getCapability(capability, pos.relative(side), side.getOpposite());
  }

  /** Moves up to {@code max} items from {@code from} to {@code to}. Returns the number moved. */
  public static int items(IItemHandler from, IItemHandler to, int max) {
    int moved = 0;
    for (int slot = 0; slot < from.getSlots() && moved < max; slot++) {
      ItemStack available = from.extractItem(slot, max - moved, true);
      if (available.isEmpty()) continue;
      int accepted = available.getCount() - insert(to, available, true).getCount();
      if (accepted <= 0) continue;
      ItemStack taken = from.extractItem(slot, accepted, false);
      ItemStack rest = insert(to, taken, false);
      // A handler that accepted in simulation but not for real: give the rest back.
      if (!rest.isEmpty()) from.insertItem(slot, rest, false);
      moved += taken.getCount() - rest.getCount();
    }
    return moved;
  }

  /** Inserts into any slots of {@code to}, existing stacks first. Returns what did not fit. */
  public static ItemStack insert(IItemHandler to, ItemStack stack, boolean simulate) {
    ItemStack rest = stack;
    for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++)
      for (int slot = 0; slot < to.getSlots() && !rest.isEmpty(); slot++)
        if (to.getStackInSlot(slot).isEmpty() == (pass == 1))
          rest = to.insertItem(slot, rest, simulate);
    return rest;
  }

  /** Moves up to {@code max} mB from {@code from} to {@code to}. Returns the amount moved. */
  public static int fluids(IFluidHandler from, IFluidHandler to, int max) {
    FluidStack moved = FluidUtil.tryFluidTransfer(to, from, max, true);
    return moved.isEmpty() ? 0 : moved.getAmount();
  }

  /** Moves up to {@code max} energy from {@code from} to {@code to}. Returns the amount moved. */
  public static int energy(IEnergyStorage from, IEnergyStorage to, int max) {
    int offered = from.extractEnergy(max, true);
    if (offered <= 0) return 0;
    int accepted = to.receiveEnergy(offered, true);
    if (accepted <= 0) return 0;
    int taken = from.extractEnergy(accepted, false);
    return to.receiveEnergy(taken, false);
  }

  /** Pushes up to {@code max} items into the neighbour on {@code side}. */
  public static int pushItems(
      Level level, BlockPos pos, Direction side, IItemHandler from, int max) {
    IItemHandler to = neighbour(Capabilities.ItemHandler.BLOCK, level, pos, side);
    return to == null ? 0 : items(from, to, max);
  }

  /** Pulls up to {@code max} items from the neighbour on {@code side}. */
  public static int pullItems(Level level, BlockPos pos, Direction side, IItemHandler to, int max) {
    IItemHandler from = neighbour(Capabilities.ItemHandler.BLOCK, level, pos, side);
    return from == null ? 0 : items(from, to, max);
  }

  /** Pushes up to {@code max} mB into the neighbour on {@code side}. */
  public static int pushFluids(
      Level level, BlockPos pos, Direction side, IFluidHandler from, int max) {
    IFluidHandler to = neighbour(Capabilities.FluidHandler.BLOCK, level, pos, side);
    return to == null ? 0 : fluids(from, to, max);
  }

  /** Pulls up to {@code max} mB from the neighbour on {@code side}. */
  public static int pullFluids(
      Level level, BlockPos pos, Direction side, IFluidHandler to, int max) {
    IFluidHandler from = neighbour(Capabilities.FluidHandler.BLOCK, level, pos, side);
    return from == null ? 0 : fluids(from, to, max);
  }

  /** Pushes up to {@code max} energy into the neighbour on {@code side}. */
  public static int pushEnergy(
      Level level, BlockPos pos, Direction side, IEnergyStorage from, int max) {
    IEnergyStorage to = neighbour(Capabilities.EnergyStorage.BLOCK, level, pos, side);
    return to == null ? 0 : energy(from, to, max);
  }
}
