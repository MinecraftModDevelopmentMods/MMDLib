package zone.moddev.mc.lib.machine.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import zone.moddev.mc.lib.api.automation.AutomationInventory;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;

/** Powered specialization of the common machine lifecycle, preserving the code-defined host API. */
public abstract class BasePoweredMachine extends BaseMachine {
  public static final ResourceLocation SCREEN_TEMPLATE =
      ResourceLocation.parse("mmdlib:base_powered_machine");
  public final EnergyBuffer energy;

  protected BasePoweredMachine(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    this(type, pos, state, 100_000, 1_000, 0);
  }

  protected BasePoweredMachine(
      BlockEntityType<?> type,
      BlockPos pos,
      BlockState state,
      long capacity,
      long maxInput,
      long maxOutput) {
    super(type, pos, state);
    energy = new EnergyBuffer(capacity, maxInput, maxOutput).onChange(this::setChanged);
  }

  @Override
  protected EnergyBuffer powerStorage() {
    return energy;
  }

  protected final MachineInventoryLifecycle poweredInventory(AutomationInventory automation) {
    return genericInventory(automation);
  }
}
