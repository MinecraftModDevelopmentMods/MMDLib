package zone.moddev.mc.lib.machine.recipe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;

/**
 * What a machine consumes and produces, by group key, and how long it takes.
 * JSON: {@code {"inputs": {"input": [...], "energy": [{"energy": 4000}]},
 * "outputs": {"output": [...]}, "time": 200}}. A {@link MachineRecipeType}
 * makes these datapack recipes; {@link RecipeMatcher} runs them.
 *
 * @param time
 *            ticks, at least 1
 */
public record MachineRecipe(Map<String, List<MachineIngredient>> inputs,
    Map<String, List<MachineProduct>> outputs, int time) {
    /** The JSON form; at least one input is required. */
    public static final MapCodec<MachineRecipe> CODEC = RecordCodecBuilder
        .<MachineRecipe>mapCodec(instance -> instance
            .group(
                Codec
                    .unboundedMap(Codec.STRING,
                        MachineIngredient.CODEC.listOf())
                    .fieldOf("inputs").forGetter(MachineRecipe::inputs),
                Codec.unboundedMap(Codec.STRING, MachineProduct.CODEC.listOf())
                    .optionalFieldOf("outputs", Map.of())
                    .forGetter(MachineRecipe::outputs),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("time")
                    .forGetter(MachineRecipe::time))
            .apply(instance, MachineRecipe::new))
        .validate(
            recipe -> recipe.inputs.values().stream().allMatch(List::isEmpty)
                ? DataResult
                    .error(() -> "A machine recipe needs at least one input")
                : DataResult.success(recipe));

    /** The network form; group order is kept. */
    public static final StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> STREAM_CODEC = StreamCodec
        .composite(groups(MachineIngredient.STREAM_CODEC),
            MachineRecipe::inputs, groups(MachineProduct.STREAM_CODEC),
            MachineRecipe::outputs, ByteBufCodecs.VAR_INT, MachineRecipe::time,
            MachineRecipe::new);

    /** Copies the maps and lists. */
    public MachineRecipe {
        inputs = copy(inputs);
        outputs = copy(outputs);
    }

    private static <T> StreamCodec<RegistryFriendlyByteBuf, Map<String, List<T>>> groups(
        StreamCodec<RegistryFriendlyByteBuf, T> entry) {
        return ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8,
            entry.apply(ByteBufCodecs.list()));
    }

    private static <T> Map<String, List<T>> copy(Map<String, List<T>> map) {
        var copy = new LinkedHashMap<String, List<T>>();
        map.forEach((key, list) -> copy.put(key, List.copyOf(list)));
        return Collections.unmodifiableMap(copy);
    }
}
