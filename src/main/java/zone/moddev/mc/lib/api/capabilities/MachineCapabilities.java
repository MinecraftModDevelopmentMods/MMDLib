package zone.moddev.mc.lib.api.capabilities;

import java.util.function.BiFunction;
import java.util.function.Function;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Registers a block entity's {@link ItemStorage}, {@link FluidStorage} or {@link EnergyBuffer} as
 * its item, fluid or energy capability, from the mod's {@link RegisterCapabilitiesEvent} handler:
 *
 * <pre>{@code
 * MachineCapabilities.items(event, CRUSHER.get(), CrusherEntity::items,
 *     (crusher, side) -> crusher.itemSides().mode(crusher.getBlockState(), side));
 * MachineCapabilities.energy(event, CRUSHER.get(), CrusherEntity::energy);
 * }</pre>
 *
 * <p>Each face gets a view of the same storage whose mode is read on every call, so neither
 * side-configuration changes nor rotation need {@code invalidateCapabilities}. A null side (unsided
 * access, e.g. information mods) gets {@link IoMode#BOTH}. A getter returning null means the block
 * entity has no such storage.
 */
public final class MachineCapabilities {
  private MachineCapabilities() {}

  /** Item capability with a per-side mode. */
  public static <B extends BlockEntity> void items(
      RegisterCapabilitiesEvent event,
      BlockEntityType<B> type,
      Function<B, ItemStorage> storage,
      BiFunction<B, Direction, IoMode> sides) {
    event.registerBlockEntity(
        Capabilities.ItemHandler.BLOCK,
        type,
        (be, side) -> {
          ItemStorage items = storage.apply(be);
          if (items == null) return null;
          return side == null ? items.view(IoMode.BOTH) : items.view(() -> sides.apply(be, side));
        });
  }

  /** Item capability, every side {@link IoMode#BOTH} (slot roles still apply). */
  public static <B extends BlockEntity> void items(
      RegisterCapabilitiesEvent event, BlockEntityType<B> type, Function<B, ItemStorage> storage) {
    items(event, type, storage, (be, side) -> IoMode.BOTH);
  }

  /** Fluid capability with a per-side mode. */
  public static <B extends BlockEntity> void fluids(
      RegisterCapabilitiesEvent event,
      BlockEntityType<B> type,
      Function<B, FluidStorage> storage,
      BiFunction<B, Direction, IoMode> sides) {
    event.registerBlockEntity(
        Capabilities.FluidHandler.BLOCK,
        type,
        (be, side) -> {
          FluidStorage fluids = storage.apply(be);
          if (fluids == null) return null;
          return side == null ? fluids.view(IoMode.BOTH) : fluids.view(() -> sides.apply(be, side));
        });
  }

  /** Fluid capability, every side {@link IoMode#BOTH} (tank roles still apply). */
  public static <B extends BlockEntity> void fluids(
      RegisterCapabilitiesEvent event, BlockEntityType<B> type, Function<B, FluidStorage> storage) {
    fluids(event, type, storage, (be, side) -> IoMode.BOTH);
  }

  /** Energy capability with a per-side mode. */
  public static <B extends BlockEntity> void energy(
      RegisterCapabilitiesEvent event,
      BlockEntityType<B> type,
      Function<B, EnergyBuffer> storage,
      BiFunction<B, Direction, IoMode> sides) {
    event.registerBlockEntity(
        Capabilities.EnergyStorage.BLOCK,
        type,
        (be, side) -> {
          EnergyBuffer energy = storage.apply(be);
          if (energy == null) return null;
          return side == null ? energy.view(IoMode.BOTH) : energy.view(() -> sides.apply(be, side));
        });
  }

  /** Energy capability, every side {@link IoMode#BOTH} (transfer rates still apply). */
  public static <B extends BlockEntity> void energy(
      RegisterCapabilitiesEvent event, BlockEntityType<B> type, Function<B, EnergyBuffer> storage) {
    energy(event, type, storage, (be, side) -> IoMode.BOTH);
  }
}
