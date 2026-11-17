package zone.moddev.mc.lib.common.runtime;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
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
    private ModCreativeTabs() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(ModCreativeTabs::registerTabs);
        bus.addListener(ModCreativeTabs::contents);
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(
            () -> MMDLib.creativeTabs().forEach(CreativeTabs::verify)));
    }

    private static void registerTabs(RegisterEvent event) {
        event.register(Registries.CREATIVE_MODE_TAB, helper -> {
            var declarations = definitions();
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
            // A mod without an authored or Java tab still gets its own tab,
            // never a shared catch-all.
            var fallbackIcons = new TreeMap<String, Item>();
            generatedItems().forEach((id, item) -> fallbackIcons
                .putIfAbsent(id.getNamespace(), item));
            fallbackIcons.forEach((namespace, icon) -> {
                var id = ResourceLocation.fromNamespaceAndPath(namespace,
                    "mmdlib_content");
                if (!declarations.containsKey(id)
                    && !BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(id))
                    helper.register(id,
                        CreativeModeTab.builder().title(modTitle(namespace))
                            .icon(() -> new ItemStack(icon))
                            .displayItems((parameters, output) -> {
                            }).build());
            });
        });
    }

    public static Component modTitle(String namespace) {
        String name = ModList.get().getModContainerById(namespace)
            .map(container -> container.getModInfo().getDisplayName())
            .orElse(namespace);
        return Component.translatableWithFallback("itemGroup." + namespace,
            name);
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
        var registered = BuiltInRegistries.CREATIVE_MODE_TAB.keySet();
        var definitions = definitions();
        if (choose(id.getNamespace(), definitions, registered)
            .filter(id::equals).isEmpty())
            return;
        var definition = definitions.get(id);
        var excluded = definition == null
            ? java.util.Set.<Item>of()
            : CreativeTabs.excludedItems(definition);
        generatedItems().forEach((itemId, item) -> {
            if (!itemId.getNamespace().equals(id.getNamespace())
                || !item.isEnabled(event.getFlags()) || excluded.contains(item))
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
