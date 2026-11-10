package zone.moddev.mc.lib.machine.ingredient;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.handler.codec.DecoderException;

import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;

/**
 * Some amount of something a machine consumes: items, fluid or energy. JSON is
 * one of {@code {"items": <ingredient>, "count": 1}}, {@code {"fluids":
 * "minecraft:water", "amount": 250}} or {@code {"energy": 4000}}.
 */
public sealed interface MachineIngredient permits MachineIngredient.ItemPart,
    MachineIngredient.FluidPart, MachineIngredient.EnergyPart {
    /** The amount: an item count, mB, or FE. */
    long amount();

    /**
     * The JSON form, picked by which of {@code items}, {@code fluids} or
     * {@code energy} is present.
     */
    Codec<MachineIngredient> CODEC = new Dispatch().codec();

    /**
     * The network form: a kind byte, then the record. Item tags arrive as the
     * items in them.
     */
    StreamCodec<RegistryFriendlyByteBuf, MachineIngredient> STREAM_CODEC = StreamCodec
        .of((buffer, ingredient) -> {
            switch (ingredient) {
                case ItemPart part -> {
                    buffer.writeByte(0);
                    ItemPart.STREAM_CODEC.encode(buffer, part);
                }
                case FluidPart part -> {
                    buffer.writeByte(1);
                    FluidPart.STREAM_CODEC.encode(buffer, part);
                }
                case EnergyPart part -> {
                    buffer.writeByte(2);
                    EnergyPart.STREAM_CODEC.encode(buffer, part);
                }
            }
        }, buffer -> {
            int kind = buffer.readByte();
            return switch (kind) {
                case 0 -> ItemPart.STREAM_CODEC.decode(buffer);
                case 1 -> FluidPart.STREAM_CODEC.decode(buffer);
                case 2 -> EnergyPart.STREAM_CODEC.decode(buffer);
                default -> throw new DecoderException(
                    "Unknown machine ingredient kind " + kind);
            };
        });

    /**
     * Items matching a vanilla {@link Ingredient} (an item, a tag or a custom
     * ingredient).
     *
     * @param count
     *            how many, at least 1
     */
    record ItemPart(Ingredient items, int count) implements MachineIngredient {
        static final MapCodec<ItemPart> CODEC = RecordCodecBuilder
            .mapCodec(instance -> instance
                .group(
                    Ingredient.CODEC_NONEMPTY.fieldOf("items")
                        .forGetter(ItemPart::items),
                    Codec.intRange(1, Integer.MAX_VALUE)
                        .optionalFieldOf("count", 1).forGetter(ItemPart::count))
                .apply(instance, ItemPart::new));
        static final StreamCodec<RegistryFriendlyByteBuf, ItemPart> STREAM_CODEC = StreamCodec
            .composite(Ingredient.CONTENTS_STREAM_CODEC, ItemPart::items,
                ByteBufCodecs.VAR_INT, ItemPart::count, ItemPart::new);

        @Override
        public long amount() {
            return count;
        }
    }

    /**
     * Fluid from a set: one ID, a {@code #tag}, or a list.
     *
     * @param millibuckets
     *            the amount in mB, at least 1; JSON {@code amount}
     */
    record FluidPart(HolderSet<Fluid> fluids, int millibuckets)
        implements MachineIngredient {
        static final MapCodec<FluidPart> CODEC = RecordCodecBuilder
            .mapCodec(instance -> instance
                .group(
                    RegistryCodecs.homogeneousList(Registries.FLUID)
                        .fieldOf("fluids").forGetter(FluidPart::fluids),
                    Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount")
                        .forGetter(FluidPart::millibuckets))
                .apply(instance, FluidPart::new));
        static final StreamCodec<RegistryFriendlyByteBuf, FluidPart> STREAM_CODEC = StreamCodec
            .composite(ByteBufCodecs.holderSet(Registries.FLUID),
                FluidPart::fluids, ByteBufCodecs.VAR_INT,
                FluidPart::millibuckets, FluidPart::new);

        @Override
        public long amount() {
            return millibuckets;
        }
    }

    /**
     * Energy in FE; a {@code long}, so costs above 2,147,483,647 FE work.
     *
     * @param amount
     *            FE, at least 1
     */
    record EnergyPart(long amount) implements MachineIngredient {
        static final MapCodec<EnergyPart> CODEC = Codec.LONG
            .validate(value -> value >= 1
                ? DataResult.success(value)
                : DataResult.error(() -> "Energy must be at least 1: " + value))
            .fieldOf("energy").xmap(EnergyPart::new, EnergyPart::amount);
        static final StreamCodec<RegistryFriendlyByteBuf, EnergyPart> STREAM_CODEC = ByteBufCodecs.VAR_LONG
            .map(EnergyPart::new, EnergyPart::amount).cast();
    }

    /**
     * Picks the record by its key; none, or more than one, is an error naming
     * the three keys.
     */
    final class Dispatch extends MapCodec<MachineIngredient> {
        private static final List<String> KEYS = List.of("items", "fluids",
            "energy");

        private Dispatch() {
        }

        @Override
        public <T> java.util.stream.Stream<T> keys(DynamicOps<T> ops) {
            return KEYS.stream().map(ops::createString);
        }

        @Override
        public <T> DataResult<MachineIngredient> decode(DynamicOps<T> ops,
            MapLike<T> input) {
            var present = KEYS.stream().filter(key -> input.get(key) != null)
                .toList();
            if (present.size() != 1)
                return DataResult.error(
                    () -> "A machine ingredient needs exactly one of 'items', 'fluids' or 'energy'; found "
                        + present);
            return switch (present.getFirst()) {
                case "items" ->
                    ItemPart.CODEC.decode(ops, input).map(part -> part);
                case "fluids" ->
                    FluidPart.CODEC.decode(ops, input).map(part -> part);
                default ->
                    EnergyPart.CODEC.decode(ops, input).map(part -> part);
            };
        }

        @Override
        public <T> RecordBuilder<T> encode(MachineIngredient input,
            DynamicOps<T> ops, RecordBuilder<T> prefix) {
            return switch (input) {
                case ItemPart part -> ItemPart.CODEC.encode(part, ops, prefix);
                case FluidPart part ->
                    FluidPart.CODEC.encode(part, ops, prefix);
                case EnergyPart part ->
                    EnergyPart.CODEC.encode(part, ops, prefix);
            };
        }
    }
}
