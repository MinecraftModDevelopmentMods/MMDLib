package zone.moddev.mc.lib.machine.ingredient;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * A fluid and an amount in mB, owned by the machine core so it means the same on every loader.
 * JSON: {@code {"id": "minecraft:water", "amount": 250, "components": {...}}}, with {@code
 * components} optional.
 */
public record FluidAmount(Holder<Fluid> fluid, DataComponentPatch components, int amount) {
  /** No fluid. */
  public static final FluidAmount EMPTY =
      new FluidAmount(Fluids.EMPTY.builtInRegistryHolder(), DataComponentPatch.EMPTY, 0);

  /** The JSON form; {@code amount} is at least 1. */
  public static final Codec<FluidAmount> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      BuiltInRegistries.FLUID
                          .holderByNameCodec()
                          .fieldOf("id")
                          .forGetter(FluidAmount::fluid),
                      DataComponentPatch.CODEC
                          .optionalFieldOf("components", DataComponentPatch.EMPTY)
                          .forGetter(FluidAmount::components),
                      Codec.intRange(1, Integer.MAX_VALUE)
                          .fieldOf("amount")
                          .forGetter(FluidAmount::amount))
                  .apply(instance, FluidAmount::new));

  /** The network form. */
  public static final StreamCodec<RegistryFriendlyByteBuf, FluidAmount> STREAM_CODEC =
      StreamCodec.composite(
          ByteBufCodecs.holderRegistry(Registries.FLUID),
          FluidAmount::fluid,
          DataComponentPatch.STREAM_CODEC,
          FluidAmount::components,
          ByteBufCodecs.VAR_INT,
          FluidAmount::amount,
          FluidAmount::new);

  /**
   * @throws IllegalArgumentException if {@code amount} is negative
   */
  public FluidAmount {
    Objects.requireNonNull(fluid, "fluid");
    Objects.requireNonNull(components, "components");
    if (amount < 0) throw new IllegalArgumentException("Fluid amount is negative: " + amount);
  }

  /** Whether there is no fluid or no amount. */
  public boolean isEmpty() {
    return amount == 0 || fluid.value() == Fluids.EMPTY;
  }

  /** Whether {@code other} is the same fluid with the same components, whatever the amounts. */
  public boolean sameFluid(FluidAmount other) {
    return fluid.value() == other.fluid.value() && components.equals(other.components);
  }

  /** This fluid with a different amount. */
  public FluidAmount withAmount(int amount) {
    return new FluidAmount(fluid, components, amount);
  }
}
