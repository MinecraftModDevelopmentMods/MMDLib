package zone.moddev.mc.lib.gui.value;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Named value providers, which a screen element reads with
 * {@code value="@namespace:id"} (or {@code max}) instead of a Java getter. A
 * provider declares the {@link ValueType} it returns and reads it from the
 * screen's block entity, on the server, every tick the screen is open.
 *
 * <p>
 * Register providers during mod construction, on both sides, as for value
 * types: screens bind on both, and the registry freezes when screens load.
 * CraftTweaker scripts register theirs through
 * {@code mods.mmdlib.ScreenValues}.
 */
public final class ScreenValueProviders {
    /** The providers screens use. */
    public static final ScreenValueProviders GLOBAL = new ScreenValueProviders();

    /**
     * A provider.
     *
     * @param kind the type of value it returns
     * @param reader reads the value from the screen's block entity; may return
     *            null for no value
     */
    public record Provider(ValueType<?> kind, Function<BlockEntity, ?> reader) {
        /** Requires both values. */
        public Provider {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(reader);
        }
    }

    private final Map<ResourceLocation, Provider> providers = new TreeMap<>();
    private boolean frozen;

    /**
     * An empty registry; screens use {@link #GLOBAL}, others are for isolated
     * binding and tests.
     */
    public ScreenValueProviders() {
    }

    /**
     * Registers the provider {@code id}.
     *
     * @throws IllegalArgumentException if {@code id} is already registered
     * @throws IllegalStateException once screens have loaded
     */
    public synchronized <T> void register(ResourceLocation id,
        ValueType<T> kind, Function<BlockEntity, ? extends T> reader) {
        Objects.requireNonNull(id);
        if (frozen)
            throw new IllegalStateException("Screen value providers are frozen;"
                + " register during mod construction");
        if (providers.containsKey(id))
            throw new IllegalArgumentException(
                "Duplicate screen value provider: " + id);
        providers.put(id, new Provider(kind, reader));
    }

    /** The provider {@code id}, if registered. */
    public synchronized Optional<Provider> get(ResourceLocation id) {
        return Optional.ofNullable(providers.get(id));
    }

    /** Every provider, by ID. */
    public synchronized Map<ResourceLocation, Provider> all() {
        return Map.copyOf(providers);
    }

    /** Stops further registration; called when screens load. */
    public synchronized void freeze() {
        frozen = true;
    }
}
