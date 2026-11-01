package zone.moddev.mc.lib.machine.recipe;

import java.util.List;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
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

  /** The JSON form: exactly one of {@code item} or {@code fluid}. */
  Codec<MachineProduct> CODEC = new Dispatch().codec();

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

  /** Picks the record by its key; none, or both, is an error naming the two keys. */
  final class Dispatch extends MapCodec<MachineProduct> {
    private static final List<String> KEYS = List.of("item", "fluid");

    private Dispatch() {}

    @Override
    public <T> Stream<T> keys(DynamicOps<T> ops) {
      return KEYS.stream().map(ops::createString);
    }

    @Override
    public <T> DataResult<MachineProduct> decode(DynamicOps<T> ops, MapLike<T> input) {
      var present = KEYS.stream().filter(key -> input.get(key) != null).toList();
      if (present.size() != 1)
        return DataResult.error(
            () -> "A machine product needs exactly one of 'item' or 'fluid'; found " + present);
      return present.getFirst().equals("item")
          ? ItemProduct.CODEC.decode(ops, input).map(product -> product)
          : FluidProduct.CODEC.decode(ops, input).map(product -> product);
    }

    @Override
    public <T> RecordBuilder<T> encode(
        MachineProduct input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
      return switch (input) {
        case ItemProduct product -> ItemProduct.CODEC.encode(product, ops, prefix);
        case FluidProduct product -> FluidProduct.CODEC.encode(product, ops, prefix);
      };
    }
  }
}
