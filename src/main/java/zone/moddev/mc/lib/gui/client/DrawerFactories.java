package zone.moddev.mc.lib.gui.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiFunction;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.drawer.BuiltinDrawers;
import zone.moddev.mc.lib.gui.drawer.DrawerDefinition;

/** Client factories keyed by common drawer type. Register during client setup. */
public final class DrawerFactories {
    public static final DrawerFactories GLOBAL = builtins();
    private final Map<ResourceLocation, BiFunction<MachineScreen, DrawerDefinition, Drawer>> factories = new HashMap<>();
    private final Set<Drawer> issued = Collections.newSetFromMap(new WeakHashMap<>());

    public static DrawerFactories builtins() {
        var registry = new DrawerFactories();
        registry.register(BuiltinDrawers.STATS, StatsDrawer::new);
        registry.register(
            BuiltinDrawers.BACKPACK,
            (screen, definition) -> new ContainerDrawer(screen, definition, false));
        registry.register(
            BuiltinDrawers.CURIOS,
            (screen, definition) -> new ContainerDrawer(screen, definition, true));
        registry.register(BuiltinDrawers.SIDES, SideConfigDrawer::new);
        registry.register(BuiltinDrawers.REDSTONE, RedstoneDrawer::new);
        registry.register(BuiltinDrawers.POPOUT, PopoutDrawer::new);
        return registry;
    }

    public synchronized void register(
        ResourceLocation type, BiFunction<MachineScreen, DrawerDefinition, Drawer> factory) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(factory);
        if (factories.putIfAbsent(type, factory) != null)
            throw new IllegalArgumentException("Duplicate drawer factory: " + type);
    }

    public synchronized void replace(
        ResourceLocation type, BiFunction<MachineScreen, DrawerDefinition, Drawer> factory) {
        if (!factories.containsKey(type))
            throw new IllegalArgumentException("Unknown drawer factory: " + type);
        factories.put(type, Objects.requireNonNull(factory));
    }

    public synchronized void validate(List<DrawerDefinition> definitions) {
        for (var definition : definitions)
            if (definition.interactive() && !factories.containsKey(definition.type()))
                throw new IllegalArgumentException(
                    "No client drawer factory registered for " + definition.type());
    }

    synchronized Drawer create(MachineScreen screen, DrawerDefinition definition) {
        var factory = factories.get(definition.type());
        if (factory == null)
            throw new IllegalArgumentException(
                "No client drawer factory registered for " + definition.type());
        var drawer = Objects.requireNonNull(factory.apply(screen, definition),
            "Drawer factory returned null");
        if (drawer.screen != screen || !issued.add(drawer))
            throw new IllegalArgumentException(
                "Drawer factories must create fresh instances for this screen");
        drawer.attach(definition);
        return drawer;
    }
}
