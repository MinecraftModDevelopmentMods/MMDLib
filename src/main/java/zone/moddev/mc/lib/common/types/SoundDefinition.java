package zone.moddev.mc.lib.common.types;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ExtraCodecs;

/**
 * A sound event declared in {@code mmdlib/sound/<path>.json}. The file names
 * the event; what it sounds like comes from the owning mod's, or a resource
 * pack's, {@code sounds.json}, as for any other sound event.
 *
 * @param range the fixed distance, in blocks, it can be heard from; empty for
 *            vanilla's default, which grows with the volume it is played at
 */
public record SoundDefinition(Optional<Float> range) {
    /** {@code {"range": 24}}, or {@code {}} for the default range. */
    public static final Codec<SoundDefinition> CODEC = RecordCodecBuilder
        .create(i -> i
            .group(ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("range")
                .forGetter(SoundDefinition::range))
            .apply(i, SoundDefinition::new));

    /** Requires {@code range} to be present or empty, not null. */
    public SoundDefinition {
        java.util.Objects.requireNonNull(range);
    }

    /**
     * The sound event {@code id} to play: the registered one, or else an
     * unregistered one with the default range, which still plays whatever
     * {@code sounds.json} defines for {@code id}.
     */
    public static SoundEvent event(ResourceLocation id) {
        return BuiltInRegistries.SOUND_EVENT.getOptional(id)
            .orElseGet(() -> SoundEvent.createVariableRangeEvent(id));
    }

    /** The sound event {@code id} this defines. */
    public SoundEvent create(ResourceLocation id) {
        return range.map(r -> SoundEvent.createFixedRangeEvent(id, r))
            .orElseGet(() -> SoundEvent.createVariableRangeEvent(id));
    }
}
