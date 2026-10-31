package zone.moddev.mc.lib.machine.recipe;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.machine.ingredient.FluidAmount;

/**
 * Something a recipe produces, possibly only some of the time. JSON is {@code {"item": {"id": ...,
 * "count": 2}, "chance": 0.25}} or {@code {"fluid": {"id": ..., "amount": 250}}}; {@code chance}
 * defaults to 1 and must be above 0 and at most 1.
 */
public sealed interface MachineProduct
    permits MachineProduct.ItemProduct, MachineProduct.FluidProduct {
  /** The chance this product is made on a run, in (0, 1]. */
  float chance();

  /** The JSON form. */
  Codec<MachineProduct> CODEC =
      Codec.either(ItemProduct.CODEC.codec(), FluidProduct.CODEC.codec())
          .xmap(
              either -> either.map(item -> (MachineProduct) item, fluid -> fluid),
              product ->
                  switch (product) {
                    case ItemProduct item -> Either.left(item);
                    case FluidProduct fluid -> Either.right(fluid);
                  });

  /** An item stack. */
  record ItemProduct(ItemStack stack, float chance) implements MachineProduct {
    static final MapCodec<ItemProduct> CODEC =
        RecordCodecBuilder.mapCodec(
            instance ->
                instance
                    .group(
                        ItemStack.CODEC.fieldOf("item").forGetter(ItemProduct::stack),
                        chanceField().forGetter(ItemProduct::chance))
                    .apply(instance, ItemProduct::new));

    /** Copies the stack. */
    public ItemProduct {
      stack = stack.copy();
    }

    /** A copy of the stack. */
    @Override
    public ItemStack stack() {
      return stack.copy();
    }
  }

  /** A fluid amount. */
  record FluidProduct(FluidAmount fluid, float chance) implements MachineProduct {
    static final MapCodec<FluidProduct> CODEC =
        RecordCodecBuilder.mapCodec(
            instance ->
                instance
                    .group(
                        FluidAmount.CODEC.fieldOf("fluid").forGetter(FluidProduct::fluid),
                        chanceField().forGetter(FluidProduct::chance))
                    .apply(instance, FluidProduct::new));
  }

  private static MapCodec<Float> chanceField() {
    return Codec.FLOAT
        .validate(
            chance ->
                chance > 0 && chance <= 1
                    ? DataResult.success(chance)
                    : DataResult.error(() -> "Product chance must be in (0, 1]: " + chance))
        .optionalFieldOf("chance", 1F);
  }
}
