package zone.moddev.mc.lib.machine.definition;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.drawer.BuiltinDrawers;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.machine.feature.MachineFeature;

/** XML feature types. Register mod types during construction on both sides. */
public final class MachineFeatureTypes {
    private record Type(Set<String> options,
        BiFunction<MachineFeatureDefinition, ScreenHost, MachineFeature<?>> factory) {
    }

    private static final Map<ResourceLocation, Type> TYPES = new HashMap<>();

    static {
        builtin(BuiltinDrawers.REDSTONE,
            Set.of("pause", "default-mode", "comparator-source"));
        builtin(BuiltinDrawers.UPGRADES,
            Set.of("gearbox", "capacitor", "gearbox-slot", "capacitor-slot",
                "range-slots", "range-storage", "legacy-layout"));
        for (var type : Set.of(BuiltinDrawers.SIDES, BuiltinDrawers.STATS,
            BuiltinDrawers.INFO, BuiltinDrawers.CURIOS, BuiltinDrawers.BACKPACK,
            BuiltinDrawers.POPOUT, BuiltinDrawers.SIDE_TAB))
            builtin(type, Set.of());
    }

    private MachineFeatureTypes() {
    }

    private static void builtin(ResourceLocation id, Set<String> options) {
        TYPES.put(id, new Type(options, null));
    }

    /**
     * Register a factory that creates a fresh feature per block entity. The
     * feature's key must match the declaration ID used for persistence.
     */
    public static synchronized void register(ResourceLocation id,
        Set<String> options,
        BiFunction<MachineFeatureDefinition, ScreenHost, MachineFeature<?>> factory) {
        var type = new Type(Set.copyOf(options),
            Objects.requireNonNull(factory));
        if (TYPES.putIfAbsent(Objects.requireNonNull(id), type) != null)
            throw new IllegalArgumentException(
                "Duplicate machine feature type: " + id);
    }

    static synchronized Set<String> options(ResourceLocation id) {
        return type(id).options();
    }

    static synchronized boolean builtin(ResourceLocation id) {
        return type(id).factory() == null;
    }

    public static MachineFeature<?> create(MachineFeatureDefinition definition,
        ScreenHost host) {
        Type type;
        synchronized (MachineFeatureTypes.class) {
            type = type(definition.type());
        }
        if (!definition.enabled() || type.factory() == null)
            return null;
        var feature = Objects
            .requireNonNull(type.factory().apply(definition, host));
        if (!definition.id().equals(feature.key()))
            throw new IllegalArgumentException(
                "Feature key must match its ID: " + definition.id());
        return feature;
    }

    private static Type type(ResourceLocation id) {
        var type = TYPES.get(id);
        if (type == null)
            throw new IllegalArgumentException(
                "Unknown machine feature type: " + id);
        return type;
    }
}
