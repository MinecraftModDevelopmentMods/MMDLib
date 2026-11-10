package zone.moddev.mc.lib.machine.stat;

import java.util.Optional;
import java.util.OptionalDouble;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One stat a machine declares, such as {@code speed}: its value with nothing
 * installed, and the range modifiers can move it in. JSON: {@code {"base": 1.0,
 * "min": 0.25, "max": 8}}, with {@code
 * min} and {@code max} optional.
 */
public record StatDefinition(double base, OptionalDouble min,
    OptionalDouble max) {
    /**
     * The JSON form; a non-finite value or {@code min > max} is a decode error.
     */
    public static final Codec<StatDefinition> CODEC = RecordCodecBuilder
        .<StatDefinition>create(instance -> instance
            .group(Codec.DOUBLE.fieldOf("base").forGetter(StatDefinition::base),
                Codec.DOUBLE.optionalFieldOf("min")
                    .forGetter(stat -> boxed(stat.min())),
                Codec.DOUBLE.optionalFieldOf("max")
                    .forGetter(stat -> boxed(stat.max())))
            .apply(instance, (base, min, max) -> new StatDefinition(base,
                unboxed(min), unboxed(max))))
        .validate(StatDefinition::check);

    /** The value clamped to {@link #min} and {@link #max}. */
    public double clamp(double value) {
        if (min.isPresent())
            value = Math.max(min.getAsDouble(), value);
        if (max.isPresent())
            value = Math.min(max.getAsDouble(), value);
        return value;
    }

    private static DataResult<StatDefinition> check(StatDefinition stat) {
        if (!Double.isFinite(stat.base) || !Double.isFinite(stat.min.orElse(0))
            || !Double.isFinite(stat.max.orElse(0)))
            return DataResult
                .error(() -> "Stat values must be finite: " + stat);
        if (stat.min.isPresent() && stat.max.isPresent()
            && stat.min.getAsDouble() > stat.max.getAsDouble())
            return DataResult.error(() -> "Stat min is above its max: " + stat);
        return DataResult.success(stat);
    }

    private static Optional<Double> boxed(OptionalDouble value) {
        return value.isPresent()
            ? Optional.of(value.getAsDouble())
            : Optional.empty();
    }

    private static OptionalDouble unboxed(Optional<Double> value) {
        return value.map(OptionalDouble::of).orElse(OptionalDouble.empty());
    }
}
