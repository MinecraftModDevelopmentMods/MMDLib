package zone.moddev.mc.lib.common.runtime;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.common.types.CreativeTabDefinition;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

/**
 * Routes generated content to its owner's main tab, independent of how items
 * were declared.
 */
public final class ModCreativeTabs {
    private static Map<ResourceLocation, CreativeTabDefinition> declared = Map
        .of();
    private static Map<String, Map<ResourceLocation, Item>> generated = Map
        .of();
    private static Set<String> registrarNamespaces = Set.of();
    private static Map<String, ResourceLocation> destinations = Map.of();

    private ModCreativeTabs() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(ModCreativeTabs::registerTabs);
        bus.addListener(ModCreativeTabs::contents);
        // Install after mod construction, behind native tab registrations.
        // Each owner's bus may receive RegisterEvent after MMDLib's bus.
        bus.addListener((FMLConstructModEvent event) -> event
            .enqueueWork(ModCreativeTabs::installFallbackListeners));
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            MMDLib.creativeTabs().forEach(CreativeTabs::verify);
            var selected = new TreeMap<String, ResourceLocation>();
            generated.keySet()
                .forEach(namespace -> choose(namespace, declared,
                    BuiltInRegistries.CREATIVE_MODE_TAB.keySet())
                    .ifPresent(id -> selected.put(namespace, id)));
            destinations = Map.copyOf(selected);
        }));
    }

    private static void registerTabs(RegisterEvent event) {
        event.register(Registries.CREATIVE_MODE_TAB, helper -> {
            var declarations = definitions();
            declared = Map.copyOf(declarations);
            var byNamespace = new TreeMap<String, Map<ResourceLocation, Item>>();
            generatedItems().forEach((id, item) -> byNamespace
                .computeIfAbsent(id.getNamespace(), ignored -> new TreeMap<>())
                .put(id, item));
            byNamespace.replaceAll((namespace, items) -> java.util.Collections
                .unmodifiableMap(items));
            generated = java.util.Collections.unmodifiableMap(byNamespace);
            registrarNamespaces = RuntimeContentRegistrar.instances().stream()
                .map(RuntimeContentRegistrar::namespace)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            var defaults = new java.util.HashSet<String>();
            declarations.forEach((id, definition) -> {
                if (definition.defaultForMod()
                    && !defaults.add(id.getNamespace()))
                    throw new IllegalArgumentException(
                        "Multiple default creative tabs for "
                            + id.getNamespace());
                boolean queued = RuntimeContentRegistrar.instances().stream()
                    .anyMatch(
                        registrar -> registrar.creativeTabs().containsKey(id));
                if (!queued)
                    helper.register(id, CreativeTabs.build(id, definition));
            });
        });
    }

    private static void installFallbackListeners() {
        var buses = new java.util.LinkedHashMap<String, IEventBus>();
        ModList.get().getMods().forEach(info -> ModList.get()
            .getModContainerById(info.getModId()).ifPresent(container -> {
                if (container.getEventBus() != null)
                    buses.put(info.getModId(), container.getEventBus());
            }));
        var owners = Set.copyOf(buses.keySet());
        buses.forEach((owner, bus) -> bus.addListener(EventPriority.LOWEST,
            (RegisterEvent event) -> registerFallbacks(event, owner, owners)));
    }

    private static void registerFallbacks(RegisterEvent event, String owner,
        Set<String> owners) {
        event.register(Registries.CREATIVE_MODE_TAB,
            helper -> generated.forEach((namespace, items) -> {
                // Synthetic namespaces (for example tests) have no mod bus of
                // their own.
                if (!namespace.equals(owner) && !(owner.equals(MMDLib.MODID)
                    && !owners.contains(namespace)))
                    return;
                if (!needsFallback(namespace, declared.keySet(),
                    BuiltInRegistries.CREATIVE_MODE_TAB.keySet()))
                    return;
                boolean materials = registrarNamespaces.contains(namespace);
                var id = ResourceLocation.fromNamespaceAndPath(namespace,
                    materials ? "materials" : "mmdlib_content");
                var icon = items.values().iterator().next();
                helper.register(id, CreativeModeTab.builder()
                    .title(
                        fallbackTitle(namespace, modName(namespace), materials))
                    .icon(() -> new ItemStack(icon))
                    .displayItems((parameters, output) -> {
                    }).build());
            }));
    }

    static boolean needsFallback(String namespace,
        Collection<ResourceLocation> declarations,
        Collection<ResourceLocation> registered) {
        return declarations.stream()
            .noneMatch(id -> id.getNamespace().equals(namespace))
            && registered.stream()
                .noneMatch(id -> id.getNamespace().equals(namespace));
    }

    static Component fallbackTitle(String namespace, String displayName,
        boolean materials) {
        return Component.translatableWithFallback(
            "itemGroup." + namespace + (materials ? ".materials" : ""),
            displayName);
    }

    private static String modName(String namespace) {
        return ModList.get().getModContainerById(namespace)
            .map(container -> container.getModInfo().getDisplayName())
            .orElse(namespace);
    }

    public static Component modTitle(String namespace) {
        return fallbackTitle(namespace, modName(namespace), false);
    }

    private static Map<ResourceLocation, CreativeTabDefinition> definitions() {
        var result = new TreeMap<>(MMDLib.creativeTabs());
        RuntimeContentRegistrar.instances().forEach(
            registrar -> result.putAll(registrar.creativeTabDefinitions()));
        return result;
    }

    /**
     * Deterministic fallback for Java tabs; explicit default-for-mod
     * declarations take precedence.
     */
    public static Optional<ResourceLocation> choose(String namespace,
        Map<ResourceLocation, CreativeTabDefinition> definitions,
        Collection<ResourceLocation> registered) {
        var defaults = definitions.entrySet().stream()
            .filter(entry -> entry.getKey().getNamespace().equals(namespace)
                && entry.getValue().defaultForMod())
            .map(Map.Entry::getKey).toList();
        if (defaults.size() > 1)
            throw new IllegalArgumentException(
                "Multiple default creative tabs for " + namespace);
        if (!defaults.isEmpty())
            return registered.contains(defaults.getFirst())
                ? Optional.of(defaults.getFirst())
                : Optional.empty();
        return registered.stream()
            .filter(id -> id.getNamespace().equals(namespace)).sorted(Comparator
                .<ResourceLocation>comparingInt(id -> switch (id.getPath()) {
                    case "main" -> 0;
                    case "machines" -> 1;
                    case "materials" -> 3;
                    case "mmdlib_content" -> 4;
                    default -> 2;
                }).thenComparing(ResourceLocation::toString))
            .findFirst();
    }

    private static Map<ResourceLocation, Item> generatedItems() {
        var result = new TreeMap<ResourceLocation, Item>();
        MachineContent.definitions().keySet()
            .forEach(id -> result.put(id, MachineContent.item(id)));
        RuntimeContentRegistrar.instances().forEach(registrar -> registrar
            .items().forEach((id, item) -> result.put(id, item.get())));
        return result;
    }

    private static void contents(BuildCreativeModeTabContentsEvent event) {
        var id = event.getTabKey().location();
        // Unrelated/secondary tabs do no copying, registry sorting or item
        // scans.
        if (!id.equals(destinations.get(id.getNamespace())))
            return;
        var definition = declared.get(id);
        var excluded = definition == null
            ? java.util.Set.<Item>of()
            : CreativeTabs.excludedItems(definition);
        generated.get(id.getNamespace()).forEach((itemId, item) -> {
            if (!item.isEnabled(event.getFlags()) || excluded.contains(item))
                return;
            var stack = new ItemStack(item);
            boolean parent = event.getParentEntries().contains(stack);
            boolean search = event.getSearchEntries().contains(stack);
            if (!parent || !search)
                event.accept(stack,
                    !parent && !search
                        ? CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
                        : !parent
                            ? CreativeModeTab.TabVisibility.PARENT_TAB_ONLY
                            : CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
        });
    }
}
