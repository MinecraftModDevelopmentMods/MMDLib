package zone.moddev.mc.companion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.capabilities.Capabilities;

import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;

/** Refills each tick and supplies up to 1,000 FE per adjacent face per tick. */
public final class CreativePowerEntity extends DemoEntity implements ScreenHost {
  private final ItemStorage empty = new ItemStorage(0);

  public CreativePowerEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
    energy.setRates(0, 1_000);
    energy.setStored(energy.capacity());
  }

  @Override
  public void serverTick() {
    energy.setStored(energy.capacity());
    for (var side : Direction.values()) {
      var target = worldPosition.relative(side);
      if (!level.hasChunkAt(target)) continue;
      var receiver = level.getCapability(Capabilities.EnergyStorage.BLOCK, target, side.getOpposite());
      if (receiver != null && receiver.canReceive())
        energy.extract(receiver.receiveEnergy(1_000, false), false);
    }
  }

  @Override
  public ItemStorage screenItems() {
    return empty;
  }
}
