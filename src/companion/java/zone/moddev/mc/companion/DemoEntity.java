package zone.moddev.mc.companion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;

public abstract class DemoEntity extends BlockEntity {
  public final EnergyBuffer energy = new EnergyBuffer(100_000, 1_000, 0).onChange(this::setChanged);

  protected DemoEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
  }

  public abstract void serverTick();

  public long getEnergy() {
    return energy.stored();
  }

  public long getEnergyCapacity() {
    return energy.capacity();
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.put("energy", energy.serializeNBT(registries));
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
  }
}
