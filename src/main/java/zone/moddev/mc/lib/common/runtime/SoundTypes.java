package zone.moddev.mc.lib.common.runtime;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;

import net.neoforged.neoforge.common.util.DeferredSoundType;

import zone.moddev.mc.lib.common.types.SoundTypeDefinition;

/**
 * The block sound sets a block's {@code sound} can name: vanilla's, by bare
 * name ({@code "metal"}), and those declared in {@code mmdlib/sound_type/}, by
 * ID ({@code "example:steel_plate"}). MMDLib installs the declared ones once
 * startup loading is done, before any block is created.
 */
public final class SoundTypes {
    private static volatile Map<ResourceLocation, SoundTypeDefinition> declared = Map
        .of();
    private static final Map<ResourceLocation, SoundType> resolved = new ConcurrentHashMap<>();

    private SoundTypes() {
    }

    /** Replaces the declared sound sets with {@code definitions}. */
    public static void install(
        Map<ResourceLocation, SoundTypeDefinition> definitions) {
        declared = Map.copyOf(definitions);
        resolved.clear();
    }

    /** The declared sound sets, by ID. */
    public static Map<ResourceLocation, SoundTypeDefinition> declared() {
        return declared;
    }

    /**
     * Whether {@code name} names a sound set: a vanilla one, or a declared one.
     * Declared sets are checked only for existence here; their sound events are
     * resolved by {@link #resolve}.
     */
    public static boolean exists(String name) {
        if (name.indexOf(':') < 0)
            return vanilla(name).isPresent();
        return declared.containsKey(ResourceLocation.tryParse(name));
    }

    /**
     * The sound set {@code name}. A declared set's sound events must already be
     * registered, which they are by the time blocks are created.
     *
     * @throws IllegalArgumentException for an unknown set, or a set naming an
     *             unregistered sound event
     */
    public static SoundType resolve(String name) {
        if (name.indexOf(':') < 0)
            return vanilla(name).orElseThrow(() -> new IllegalArgumentException(
                "Unknown sound set: " + name));
        var id = ResourceLocation.parse(name);
        return resolved.computeIfAbsent(id, SoundTypes::create);
    }

    private static SoundType create(ResourceLocation id) {
        var definition = declared.get(id);
        if (definition == null)
            throw new IllegalArgumentException("Unknown sound set: " + id);
        var base = vanilla(definition.base())
            .orElseThrow(() -> new IllegalArgumentException("Sound set " + id
                + ": unknown base sound set '" + definition.base() + "'"));
        // Resolved now, so a missing sound event fails here, naming the set.
        var breakSound = event(id, definition.breakSound(),
            base.getBreakSound());
        var step = event(id, definition.step(), base.getStepSound());
        var place = event(id, definition.place(), base.getPlaceSound());
        var hit = event(id, definition.hit(), base.getHitSound());
        var fall = event(id, definition.fall(), base.getFallSound());
        return new DeferredSoundType(
            definition.volume().orElse(base.getVolume()),
            definition.pitch().orElse(base.getPitch()), () -> breakSound,
            () -> step, () -> place, () -> hit, () -> fall);
    }

    private static SoundEvent event(ResourceLocation set,
        Optional<ResourceLocation> id, SoundEvent fallback) {
        if (id.isEmpty())
            return fallback;
        if (!BuiltInRegistries.SOUND_EVENT.containsKey(id.get()))
            throw new IllegalArgumentException("Sound set " + set
                + " names an unregistered sound event: " + id.get());
        return BuiltInRegistries.SOUND_EVENT.get(id.get());
    }

    private static Optional<SoundType> vanilla(String name) {
        try {
            var field = SoundType.class.getField(name.toUpperCase(Locale.ROOT));
            return field.get(null) instanceof SoundType type
                ? Optional.of(type)
                : Optional.empty();
        } catch (ReflectiveOperationException ex) {
            return Optional.empty();
        }
    }
}
