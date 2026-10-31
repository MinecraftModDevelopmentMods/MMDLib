package zone.moddev.mc.lib.machine.neoforge;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;

import net.neoforged.neoforge.registries.DeferredRegister;

import zone.moddev.mc.lib.MMDLib;

/**
 * The machine module's item data components; {@link #TYPES} is attached to the mod bus by MMDLib.
 */
public final class MachineComponents {
  /** MMDLib's machine data-component register. */
  public static final DeferredRegister.DataComponents TYPES =
      DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MMDLib.MODID);

  /**
   * {@code mmdlib:part_wear}: wear points an installed part has taken. The part breaks when this
   * reaches its part file's durability.
   */
  public static final Supplier<DataComponentType<Integer>> PART_WEAR =
      TYPES.registerComponentType(
          "part_wear",
          builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

  private MachineComponents() {}
}
