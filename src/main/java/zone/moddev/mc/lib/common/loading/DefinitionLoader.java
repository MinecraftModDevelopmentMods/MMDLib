package zone.moddev.mc.lib.common.loading;

import java.io.IOException;
import java.io.UncheckedIOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.*;
import java.util.function.Predicate;

import com.google.gson.JsonParser;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.codecs.EventExtension;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.common.modules.DataModules;
import zone.moddev.mc.lib.common.modules.ModuleCatalog;
import zone.moddev.mc.lib.common.types.CreativeTabDefinition;
import zone.moddev.mc.lib.common.types.SoundDefinition;
import zone.moddev.mc.lib.common.types.SoundTypeDefinition;
import zone.moddev.mc.lib.common.types.StandaloneBlock;
import zone.moddev.mc.lib.common.types.StandaloneItem;

/**
 * Loads every definition MMDLib finds in mods' {@code mmdlib/} directories at
 * startup. Files are discovered and decoded in parallel; they are then
 * installed in a fixed order so results do not depend on timing: extension
 * files (event handlers), then modules, then code-built definitions from
 * {@link MaterialExtensions#registerDefinitionSource} (which may add modules),
 * then materials, then standalone blocks and items from files and sources.
 * Sound events, block sound sets and creative tabs, which depend on nothing,
 * are decoded with the modules. See {@code docs/definition-loading.md}.
 */
public final class DefinitionLoader {
    private DefinitionLoader() {
    }

    private static final String STANDALONE_BLOCKS = "standalone/block";
    private static final String STANDALONE_ITEMS = "standalone/item";
    private static final String SOUNDS = "sound";
    private static final String SOUND_TYPES = "sound_type";
    private static final String CREATIVE_TABS = "creative_tab";

    /**
     * root contains extension/, item/, block/, fluid/, material/,
     * standalone/block/, standalone/item/, sound/, sound_type/, creative_tab/.
     * The caller owns its filesystem.
     */
    public record SourceRoot(String namespace, Path root,
        ClassLoader classLoader) {
        /**
         * Requires a valid namespace, a root and a class loader.
         *
         * @throws net.minecraft.ResourceLocationException
         *             if {@code namespace} is not a valid namespace
         */
        public SourceRoot {
            ResourceLocation.fromNamespaceAndPath(namespace, "validate");
            Objects.requireNonNull(root);
            Objects.requireNonNull(classLoader);
        }
    }

    /**
     * Everything loaded from all sources, each map sorted by ID.
     *
     * @param modules
     *            the modules found
     * @param extensions
     *            the extension files found, by file ID
     * @param materials
     *            the planned materials
     * @param standalone
     *            the planned standalone blocks
     * @param standaloneItems
     *            the planned standalone items
     * @param sounds
     *            the sound events declared
     * @param soundTypes
     *            the block sound sets declared
     * @param creativeTabs
     *            the creative tabs declared
     */
    public record Loaded(ModuleCatalog modules,
        Map<ResourceLocation, EventExtension> extensions,
        Map<ResourceLocation, MaterialFactory.Result> materials,
        Map<ResourceLocation, MaterialFactory.Result> standalone,
        Map<ResourceLocation, MaterialFactory.Result> standaloneItems,
        Map<ResourceLocation, SoundDefinition> sounds,
        Map<ResourceLocation, SoundTypeDefinition> soundTypes,
        Map<ResourceLocation, CreativeTabDefinition> creativeTabs) {
        /** Copies each map, sorted by ID. */
        public Loaded {
            extensions = immutableSorted(extensions);
            materials = immutableSorted(materials);
            standalone = immutableSorted(standalone);
            standaloneItems = immutableSorted(standaloneItems);
            sounds = immutableSorted(sounds);
            soundTypes = immutableSorted(soundTypes);
            creativeTabs = immutableSorted(creativeTabs);
        }

        /** Everything loaded, without sound events, sound sets or tabs. */
        public Loaded(ModuleCatalog modules,
            Map<ResourceLocation, EventExtension> extensions,
            Map<ResourceLocation, MaterialFactory.Result> materials,
            Map<ResourceLocation, MaterialFactory.Result> standalone,
            Map<ResourceLocation, MaterialFactory.Result> standaloneItems) {
            this(modules, extensions, materials, standalone, standaloneItems,
                Map.of(), Map.of(), Map.of());
        }

        /** Everything loaded, without standalone items. */
        public Loaded(ModuleCatalog modules,
            Map<ResourceLocation, EventExtension> extensions,
            Map<ResourceLocation, MaterialFactory.Result> materials,
            Map<ResourceLocation, MaterialFactory.Result> standalone) {
            this(modules, extensions, materials, standalone, Map.of());
        }
    }

    private record Located<T>(Path path, T value, ClassLoader classLoader) {
    }

    private static final class Batch {
        final Map<ResourceLocation, Located<DataModules.Item>> items = new TreeMap<>();
        final Map<ResourceLocation, Located<DataModules.Block>> blocks = new TreeMap<>();
        final Map<ResourceLocation, Located<DataModules.Fluid>> fluids = new TreeMap<>();
        final Map<ResourceLocation, Located<EventExtension>> extensions = new TreeMap<>();
        final Map<ResourceLocation, Located<Path>> materials = new TreeMap<>();
        final Map<ResourceLocation, Located<StandaloneBlock>> standalone = new TreeMap<>();
        final Map<ResourceLocation, Located<StandaloneItem>> standaloneItems = new TreeMap<>();
        final Map<ResourceLocation, Located<SoundDefinition>> sounds = new TreeMap<>();
        final Map<ResourceLocation, Located<SoundTypeDefinition>> soundTypes = new TreeMap<>();
        final Map<ResourceLocation, Located<CreativeTabDefinition>> creativeTabs = new TreeMap<>();

        void merge(Batch other) {
            mergeInto(items, other.items);
            mergeInto(blocks, other.blocks);
            mergeInto(fluids, other.fluids);
            mergeInto(extensions, other.extensions);
            mergeInto(materials, other.materials);
            mergeInto(standalone, other.standalone);
            mergeInto(standaloneItems, other.standaloneItems);
            mergeInto(sounds, other.sounds);
            mergeInto(soundTypes, other.soundTypes);
            mergeInto(creativeTabs, other.creativeTabs);
        }
    }

    /**
     * Loads all sources into {@code api}: installs extensions and modules, then
     * plans materials, standalone blocks and standalone items. A standalone
     * definition whose module needs a missing mod is skipped.
     *
     * @param sources
     *            the {@code mmdlib/} roots to read; order does not matter
     * @param installed
     *            tests whether a mod is loaded
     * @throws IllegalArgumentException
     *             naming the file, for invalid data, duplicate IDs or two
     *             definitions producing the same registry ID
     */
    public static Loaded load(List<SourceRoot> sources, MaterialExtensions api,
        Predicate<String> installed) {
        Objects.requireNonNull(api);
        Objects.requireNonNull(installed);
        var ordered = sources.stream()
            .sorted(Comparator.comparing(SourceRoot::namespace)
                .thenComparing(source -> source.root().toString()))
            .toList();
        var all = new Batch();
        ordered.parallelStream()
            .map(source -> discover(source, List.of("extension"))).toList()
            .forEach(all::merge);
        all.extensions
            .forEach((id, source) -> api.events().loadExtension(source.value(),
                source.classLoader(), source.path().toString()));
        ordered.parallelStream()
            .map(source -> discover(source,
                List.of("block", "item", "fluid", "material", STANDALONE_BLOCKS,
                    STANDALONE_ITEMS, SOUNDS, SOUND_TYPES, CREATIVE_TABS)))
            .toList().forEach(all::merge);
        all.items.forEach((id, source) -> install(source,
            () -> api.registerItemModule(id, source.value())));
        all.blocks.forEach((id, source) -> install(source,
            () -> api.registerBlockModule(id, source.value())));
        all.fluids.forEach((id, source) -> install(source,
            () -> api.registerFluidModule(id, source.value())));
        // Code sources run once, now: modules and callbacks they register are
        // visible to every
        // material decoded below, from files as well as from the sources
        // themselves.
        var sourced = new LinkedHashMap<String, MaterialExtensions.Definitions>();
        for (var source : api.definitionSources()) {
            var origin = "definition source '" + source.name() + "'";
            if (sourced.put(origin, Objects
                .requireNonNull(source.source().load(api), origin)) != null)
                throw new IllegalArgumentException("Duplicate " + origin);
        }
        var factory = new MaterialFactory(api, installed);
        var materials = new TreeMap<ResourceLocation, MaterialFactory.Result>();
        var concreteIds = new HashMap<String, Map<ResourceLocation, Object>>();
        var materialOrigins = new HashMap<ResourceLocation, Object>();
        all.materials.forEach((id, source) -> {
            try {
                var result = factory.load(id, source.path());
                validateConcreteIds(result, source.path(), concreteIds);
                validateBindings(result, source.path(), api);
                materials.put(id, result);
                materialOrigins.put(id, source.path());
            } catch (IOException ex) {
                throw new UncheckedIOException(
                    "Cannot read material " + source.path(), ex);
            }
        });
        for (var entry : sourced.entrySet()) {
            var origin = entry.getKey();
            for (var material : entry.getValue().materials()) {
                var id = material.name()
                    .orElseThrow(() -> new IllegalArgumentException(
                        origin + ": unnamed material"));
                var previous = materialOrigins.putIfAbsent(id, origin);
                if (previous != null)
                    throw new IllegalArgumentException("Duplicate material "
                        + id + ": " + previous + " and " + origin);
                MaterialFactory.Result result;
                try {
                    result = factory.create(material);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException(
                        origin + ": " + ex.getMessage(), ex);
                }
                validateConcreteIds(result, origin, concreteIds);
                validateBindings(result, origin, api);
                materials.put(id, result);
            }
        }
        var standalone = new TreeMap<ResourceLocation, MaterialFactory.Result>();
        all.standalone.forEach((id, source) -> install(source, () -> {
            var module = api.blockModule(source.value().module());
            if (module.requiredMod().isPresent()
                && !installed.test(module.requiredMod().get()))
                return;
            var result = source.value().resolve(id, module);
            validateConcreteIds(result, source.path(), concreteIds);
            result.content().blocks().values()
                .forEach(block -> api.events().validate(block.events()));
            standalone.put(id, result);
        }));
        for (var entry : sourced.entrySet()) {
            var origin = entry.getKey();
            new TreeMap<>(entry.getValue().standaloneBlocks())
                .forEach((id, definition) -> {
                    if (standalone.containsKey(id))
                        throw new IllegalArgumentException(
                            "Duplicate standalone block " + id + ": " + origin
                                + " and an earlier definition");
                    try {
                        var module = api.blockModule(definition.module());
                        if (module.requiredMod().isPresent()
                            && !installed.test(module.requiredMod().get()))
                            return;
                        var result = definition.resolve(id, module);
                        validateConcreteIds(result, origin, concreteIds);
                        result.content().blocks().values().forEach(
                            block -> api.events().validate(block.events()));
                        standalone.put(id, result);
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalArgumentException(
                            origin + ": " + id + ": " + ex.getMessage(), ex);
                    }
                });
        }
        var standaloneItems = new TreeMap<ResourceLocation, MaterialFactory.Result>();
        all.standaloneItems.forEach((id, source) -> install(source, () -> {
            var module = api.itemModule(source.value().module());
            if (module.requiredMod().isPresent()
                && !installed.test(module.requiredMod().get()))
                return;
            var result = source.value().resolve(id, module);
            validateConcreteIds(result, source.path(), concreteIds);
            result.content().items().values()
                .forEach(item -> api.events().validate(item.events()));
            standaloneItems.put(id, result);
        }));
        for (var entry : sourced.entrySet()) {
            var origin = entry.getKey();
            new TreeMap<>(entry.getValue().standaloneItems())
                .forEach((id, definition) -> {
                    if (standaloneItems.containsKey(id))
                        throw new IllegalArgumentException(
                            "Duplicate standalone item " + id + ": " + origin
                                + " and an earlier definition");
                    try {
                        var module = api.itemModule(definition.module());
                        if (module.requiredMod().isPresent()
                            && !installed.test(module.requiredMod().get()))
                            return;
                        var result = definition.resolve(id, module);
                        validateConcreteIds(result, origin, concreteIds);
                        result.content().items().values().forEach(
                            item -> api.events().validate(item.events()));
                        standaloneItems.put(id, result);
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalArgumentException(
                            origin + ": " + id + ": " + ex.getMessage(), ex);
                    }
                });
        }
        return new Loaded(
            new ModuleCatalog(values(all.items), values(all.blocks),
                values(all.fluids)),
            values(all.extensions), materials, standalone, standaloneItems,
            values(all.sounds), values(all.soundTypes),
            values(all.creativeTabs));
    }

    private static Batch discover(SourceRoot source, List<String> sections) {
        var batch = new Batch();
        if (!Files.isDirectory(source.root()))
            return batch;
        for (String section : sections) {
            var folder = source.root().resolve(section);
            if (!Files.isDirectory(folder))
                continue;
            try (var files = Files.walk(folder)) {
                for (var file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json")).sorted()
                    .toList()) {
                    var id = resourceId(source.namespace(), folder, file);
                    var loader = source.classLoader();
                    switch (section) {
                        case "extension" ->
                            add(batch.extensions, id, new Located<>(file,
                                decode(file, EventExtension.CODEC), loader));
                        case "item" -> add(batch.items, id, new Located<>(file,
                            decode(file, DataModules.Item.CODEC), loader));
                        case "block" ->
                            add(batch.blocks, id, new Located<>(file,
                                decode(file, DataModules.Block.CODEC), loader));
                        case "fluid" ->
                            add(batch.fluids, id, new Located<>(file,
                                decode(file, DataModules.Fluid.CODEC), loader));
                        // Selection and callback validation needs the completed
                        // catalog: do not
                        // decode yet.
                        case "material" -> add(batch.materials, id,
                            new Located<>(file, file, loader));
                        case STANDALONE_BLOCKS ->
                            add(batch.standalone, id, new Located<>(file,
                                decode(file, StandaloneBlock.CODEC), loader));
                        case STANDALONE_ITEMS ->
                            add(batch.standaloneItems, id, new Located<>(file,
                                decode(file, StandaloneItem.CODEC), loader));
                        case SOUNDS -> add(batch.sounds, id, new Located<>(file,
                            decode(file, SoundDefinition.CODEC), loader));
                        case CREATIVE_TABS -> add(batch.creativeTabs, id,
                            new Located<>(file,
                                decode(file, CreativeTabDefinition.CODEC),
                                loader));
                        case SOUND_TYPES -> add(batch.soundTypes, id,
                            new Located<>(file,
                                decode(file, SoundTypeDefinition.CODEC),
                                loader));
                        default -> throw new IllegalStateException(
                            "Unhandled definition section: " + section);
                    }
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(
                    "Cannot read definition folder " + folder, ex);
            }
        }
        return batch;
    }

    private static ResourceLocation resourceId(String namespace, Path folder,
        Path file) {
        var relative = folder.relativize(file).toString().replace('\\', '/');
        try {
            return ResourceLocation.fromNamespaceAndPath(namespace,
                relative.substring(0, relative.length() - 5));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                file + ": Invalid definition resource ID", ex);
        }
    }

    private static <T> T decode(Path file, Codec<T> codec) throws IOException {
        try (var reader = Files.newBufferedReader(file)) {
            return codec.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                .getOrThrow();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(file + ": " + ex.getMessage(),
                ex);
        }
    }

    private static void install(Located<?> source, Runnable registration) {
        try {
            registration.run();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                source.path() + ": " + ex.getMessage(), ex);
        }
    }

    private static <T> void add(Map<ResourceLocation, Located<T>> map,
        ResourceLocation id, Located<T> source) {
        var old = map.putIfAbsent(id, source);
        if (old != null)
            throw new IllegalArgumentException("Duplicate definition " + id
                + ": " + old.path() + " and " + source.path());
    }

    private static <T> void mergeInto(Map<ResourceLocation, Located<T>> target,
        Map<ResourceLocation, Located<T>> source) {
        source.forEach((id, value) -> add(target, id, value));
    }

    private static <T> Map<ResourceLocation, T> values(
        Map<ResourceLocation, Located<T>> sources) {
        var result = new TreeMap<ResourceLocation, T>();
        sources.forEach((id, source) -> result.put(id, source.value()));
        return immutableSorted(result);
    }

    private static <T> Map<ResourceLocation, T> immutableSorted(
        Map<ResourceLocation, T> values) {
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }

    /**
     * Checks every planned item's and block's event bindings against the
     * callback catalog.
     */
    private static void validateBindings(MaterialFactory.Result result,
        Object source, MaterialExtensions api) {
        var plan = result.content();
        var bindings = new LinkedHashMap<ResourceLocation, Map<String, ResourceLocation>>();
        java.util.stream.Stream
            .of(plan.items().values(), plan.extraItems().values())
            .flatMap(Collection::stream)
            .forEach(item -> bindings.put(item.name(), item.events()));
        java.util.stream.Stream
            .of(plan.blocks().values(), plan.extraBlocks().values())
            .flatMap(Collection::stream)
            .forEach(block -> bindings.put(block.name(), block.events()));
        bindings.forEach((name, events) -> {
            try {
                api.events().validate(events);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                    source + ": " + name + ": " + ex.getMessage(), ex);
            }
        });
    }

    private static void validateConcreteIds(MaterialFactory.Result result,
        Object source, Map<String, Map<ResourceLocation, Object>> registries) {
        var plan = result.content();
        plan.itemDefinitions().keySet()
            .forEach(id -> unique(registries, "item", id, source));
        plan.blockDefinitions().keySet()
            .forEach(id -> unique(registries, "block", id, source));
        plan.entities().values().forEach(
            entity -> unique(registries, "entity", entity.name(), source));
        plan.blockEntityDefinitions().keySet()
            .forEach(id -> unique(registries, "block_entity", id, source));
        if (plan.fluid() != null)
            unique(registries, "fluid", plan.fluid().name(), source);
    }

    private static void unique(
        Map<String, Map<ResourceLocation, Object>> registries, String registry,
        ResourceLocation id, Object source) {
        var previous = registries
            .computeIfAbsent(registry, key -> new HashMap<>())
            .putIfAbsent(id, source);
        if (previous != null)
            throw new IllegalArgumentException("Duplicate " + registry + " ID "
                + id + ": " + previous + " and " + source);
    }
}
