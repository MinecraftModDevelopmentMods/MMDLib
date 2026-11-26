package zone.moddev.mc.lib.gui.drawer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;

/**
 * Mod-extensible common-side providers. Register during mod construction on
 * both sides.
 */
public final class DrawerRegistry {
    public static final DrawerRegistry GLOBAL = builtins();

    private record Contribution(String featureId, DrawerDefinition drawer) {
    }

    public record Context(Screen screen, TextMeasure text, ScreenHost host,
        Set<ResourceLocation> enabled) {
        public Context {
            Objects.requireNonNull(screen);
            Objects.requireNonNull(text);
            enabled = Set.copyOf(enabled);
        }

        public Context(Screen screen, TextMeasure text, ScreenHost host) {
            this(screen, text, host, Set.of());
        }
    }

    private final Map<ResourceLocation, Function<Context, List<DrawerDefinition>>> providers = new TreeMap<>(
        Comparator.comparing(ResourceLocation::toString));

    public static DrawerRegistry builtins() {
        var registry = new DrawerRegistry();
        BuiltinDrawers.register(registry);
        return registry;
    }

    public synchronized void register(ResourceLocation type,
        Function<Context, List<DrawerDefinition>> provider) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(provider);
        if (providers.putIfAbsent(type, provider) != null)
            throw new IllegalArgumentException(
                "Duplicate drawer provider: " + type);
    }

    public synchronized void replace(ResourceLocation type,
        Function<Context, List<DrawerDefinition>> provider) {
        if (!providers.containsKey(type))
            throw new IllegalArgumentException(
                "Unknown drawer provider: " + type);
        providers.put(type, Objects.requireNonNull(provider));
    }

    public synchronized List<DrawerDefinition> resolve(Context context) {
        var contributions = new ArrayList<Contribution>();
        for (var entry : providers.entrySet())
            for (var definition : List
                .copyOf(entry.getValue().apply(context))) {
                if (!definition.type().equals(entry.getKey()))
                    throw new IllegalArgumentException(
                        "Drawer provider returned another type: "
                            + definition.type());
                contributions.add(new Contribution(null, definition));
            }
        var host = context.host();
        var features = host == null ? null : host.screenFeatures();
        if (features != null)
            for (var contributor : features
                .contributors(MachineFeatureDrawers.class))
                for (var drawer : List.copyOf(contributor.drawers(context)))
                    contributions
                        .add(new Contribution(contributor.key(), drawer));

        if (contributions.size() > 4096)
            throw new IllegalArgumentException("Too many drawer declarations");
        var result = new ArrayList<DrawerDefinition>();
        var ids = new HashSet<String>();
        var elements = Collections
            .newSetFromMap(new IdentityHashMap<Element, Boolean>());
        for (var contribution : contributions) {
            var drawer = contribution.drawer();
            drawer.element().ifPresent(element -> {
                if (context.screen().tabs().stream()
                    .noneMatch(tab -> tab == element) || !elements.add(element))
                    throw new IllegalArgumentException(
                        "Drawer element must be a unique screen tab");
            });
            var settings = host == null
                ? DrawerSettings.DEFAULT
                : host.screenDrawerSettings(contribution.featureId(), drawer);
            if (!settings.enabled())
                continue;
            var placed = settings.apply(drawer);
            if (!ids.add(placed.placement().edge() + "/" + placed.id()))
                throw new IllegalArgumentException(
                    "Duplicate drawer: " + placed.id());
            result.add(placed);
        }
        for (var tab : context.screen().tabs())
            if (!elements.contains(tab))
                throw new IllegalArgumentException(
                    "No registered drawer layout for tab: " + tab);
        result.sort(Comparator
            .comparingInt(definition -> definition.placement().order()));
        return List.copyOf(result);
    }

    /**
     * Adapter for the older standalone layout overloads. Runtime menus use the
     * host context.
     */
    public static Context legacy(Screen screen, TextMeasure text,
        boolean automation, boolean redstone) {
        var enabled = new HashSet<ResourceLocation>();
        if (automation)
            enabled.add(BuiltinDrawers.SIDES);
        if (redstone)
            enabled.add(BuiltinDrawers.REDSTONE);
        return new Context(screen, text, null, enabled);
    }
}
