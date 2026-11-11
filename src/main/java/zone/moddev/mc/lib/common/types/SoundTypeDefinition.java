package zone.moddev.mc.lib.common.types;

import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

/**
 * A block sound set declared in {@code mmdlib/sound_type/<path>.json}: the
 * sound events a block plays when broken, stepped on, placed, hit and fallen
 * on, with their volume and pitch. A block uses it by naming its ID as its
 * {@code sound}. Anything left out comes from {@code base}, one of vanilla's
 * sound sets ({@code "stone"}, {@code "metal"}, ...).
 *
 * @param base
 *            the vanilla sound set unset values come from
 * @param breakSound
 *            played when the block is broken
 * @param step
 *            played when an entity walks on it
 * @param place
 *            played when it is placed
 * @param hit
 *            played while it is being mined
 * @param fall
 *            played when an entity lands on it
 * @param volume
 *            the volume all of them play at
 * @param pitch
 *            the pitch all of them play at
 */
public record SoundTypeDefinition(String base,
    Optional<ResourceLocation> breakSound, Optional<ResourceLocation> step,
    Optional<ResourceLocation> place, Optional<ResourceLocation> hit,
    Optional<ResourceLocation> fall, Optional<Float> volume,
    Optional<Float> pitch) {
    /**
     * {@code {"base": "metal", "place": "example:block/clank", "pitch": 0.8}};
     * every field is optional, {@code base} defaults to {@code "stone"}.
     */
    public static final Codec<SoundTypeDefinition> CODEC = RecordCodecBuilder
        .create(i -> i
            .group(
                Codec.STRING.optionalFieldOf("base", "stone")
                    .forGetter(SoundTypeDefinition::base),
                ResourceLocation.CODEC.optionalFieldOf("break")
                    .forGetter(SoundTypeDefinition::breakSound),
                ResourceLocation.CODEC.optionalFieldOf("step")
                    .forGetter(SoundTypeDefinition::step),
                ResourceLocation.CODEC.optionalFieldOf("place")
                    .forGetter(SoundTypeDefinition::place),
                ResourceLocation.CODEC.optionalFieldOf("hit")
                    .forGetter(SoundTypeDefinition::hit),
                ResourceLocation.CODEC.optionalFieldOf("fall")
                    .forGetter(SoundTypeDefinition::fall),
                ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("volume")
                    .forGetter(SoundTypeDefinition::volume),
                ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("pitch")
                    .forGetter(SoundTypeDefinition::pitch))
            .apply(i, SoundTypeDefinition::new));

    /**
     * Requires every value; {@code base} must be a vanilla name, not another
     * sound set's ID.
     *
     * @throws IllegalArgumentException
     *             if {@code base} is blank or namespaced
     */
    public SoundTypeDefinition {
        Objects.requireNonNull(base);
        for (var value : java.util.List.of(breakSound, step, place, hit, fall,
            volume, pitch))
            Objects.requireNonNull(value);
        if (base.isBlank() || base.indexOf(':') >= 0)
            throw new IllegalArgumentException(
                "A sound set's base must be a vanilla sound set name, got '"
                    + base + "'");
    }
}
