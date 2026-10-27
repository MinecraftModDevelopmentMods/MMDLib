package zone.moddev.mc.lib.common.loading;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.codecs.EventExtension;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.common.modules.DataModules;
import zone.moddev.mc.lib.common.modules.ModuleCatalog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/** Parallel file discovery/decoding, followed by ordered catalog, handler, and material loading. */
public final class DefinitionLoader {
    private DefinitionLoader() {}
    /** root contains extension/, item/, block/, fluid/, material/. The caller owns its filesystem. */
    public record SourceRoot(String namespace, Path root, ClassLoader classLoader) {
        public SourceRoot {
            ResourceLocation.fromNamespaceAndPath(namespace, "validate");
            Objects.requireNonNull(root); Objects.requireNonNull(classLoader);
        }
    }
    public record Loaded(ModuleCatalog modules, Map<ResourceLocation, EventExtension> extensions,
            Map<ResourceLocation, MaterialFactory.Result> materials) {
        public Loaded { extensions = immutableSorted(extensions); materials = immutableSorted(materials); }
    }
    private record Located<T>(Path path, T value, ClassLoader classLoader) {}
    private static final class Batch {
        final Map<ResourceLocation, Located<DataModules.Item>> items = new TreeMap<>();
        final Map<ResourceLocation, Located<DataModules.Block>> blocks = new TreeMap<>();
        final Map<ResourceLocation, Located<DataModules.Fluid>> fluids = new TreeMap<>();
        final Map<ResourceLocation, Located<EventExtension>> extensions = new TreeMap<>();
        final Map<ResourceLocation, Located<Path>> materials = new TreeMap<>();
        void merge(Batch other) {
            mergeInto(items, other.items); mergeInto(blocks, other.blocks); mergeInto(fluids, other.fluids);
            mergeInto(extensions, other.extensions); mergeInto(materials, other.materials);
        }
    }
    public static Loaded load(List<SourceRoot> sources, MaterialExtensions api, Predicate<String> installed) {
        Objects.requireNonNull(api); Objects.requireNonNull(installed);
        var ordered = sources.stream().sorted(Comparator.comparing(SourceRoot::namespace)
                .thenComparing(source -> source.root().toString())).toList();
        var all = new Batch();
        ordered.parallelStream().map(source -> discover(source, List.of("extension"))).toList().forEach(all::merge);
        all.extensions.forEach((id, source) -> api.events().loadExtension(source.value(), source.classLoader(), source.path().toString()));
        ordered.parallelStream().map(source -> discover(source, List.of("block", "item", "fluid", "material"))).toList().forEach(all::merge);
        all.items.forEach((id, source) -> install(source, () -> api.registerItemModule(id, source.value())));
        all.blocks.forEach((id, source) -> install(source, () -> api.registerBlockModule(id, source.value())));
        all.fluids.forEach((id, source) -> install(source, () -> api.registerFluidModule(id, source.value())));
        var factory = new MaterialFactory(api, installed);
        var materials = new TreeMap<ResourceLocation, MaterialFactory.Result>();
        var concreteIds = new HashMap<String, Map<ResourceLocation, Path>>();
        all.materials.forEach((id, source) -> {
            try {
                var result = factory.load(id, source.path());
                validateConcreteIds(result, source.path(), concreteIds);
                for (var item : result.content().items().values()) {
                    try { api.events().validate(item.events()); }
                    catch (IllegalArgumentException ex) { throw new IllegalArgumentException(source.path() + ": " + item.name() + ": " + ex.getMessage(), ex); }
                }
                materials.put(id, result);
            } catch (IOException ex) { throw new UncheckedIOException("Cannot read material " + source.path(), ex); }
        });
        return new Loaded(new ModuleCatalog(values(all.items), values(all.blocks), values(all.fluids)),
                values(all.extensions), materials);
    }
    private static Batch discover(SourceRoot source, List<String> sections) {
        var batch = new Batch();
        if (!Files.isDirectory(source.root())) return batch;
        for (String section : sections) {
            var folder = source.root().resolve(section);
            if (!Files.isDirectory(folder)) continue;
            try (var files = Files.walk(folder)) {
                for (var file : files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                    var id = resourceId(source.namespace(), folder, file);
                    var loader = source.classLoader();
                    switch (section) {
                        case "extension" -> add(batch.extensions, id, new Located<>(file, decode(file, EventExtension.CODEC), loader));
                        case "item" -> add(batch.items, id, new Located<>(file, decode(file, DataModules.Item.CODEC), loader));
                        case "block" -> add(batch.blocks, id, new Located<>(file, decode(file, DataModules.Block.CODEC), loader));
                        case "fluid" -> add(batch.fluids, id, new Located<>(file, decode(file, DataModules.Fluid.CODEC), loader));
                        // Selection and callback validation needs the completed catalog: do not decode yet.
                        case "material" -> add(batch.materials, id, new Located<>(file, file, loader));
                        default -> throw new IllegalStateException("Unhandled definition section: " + section);
                    }
                }
            } catch (IOException ex) { throw new UncheckedIOException("Cannot read definition folder " + folder, ex); }
        }
        return batch;
    }
    private static ResourceLocation resourceId(String namespace, Path folder, Path file) {
        var relative = folder.relativize(file).toString().replace('\\', '/');
        try { return ResourceLocation.fromNamespaceAndPath(namespace, relative.substring(0, relative.length() - 5)); }
        catch (RuntimeException ex) { throw new IllegalArgumentException(file + ": Invalid definition resource ID", ex); }
    }
    private static <T> T decode(Path file, Codec<T> codec) throws IOException {
        try (var reader = Files.newBufferedReader(file)) {
            return codec.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
        } catch (RuntimeException ex) { throw new IllegalArgumentException(file + ": " + ex.getMessage(), ex); }
    }
    private static void install(Located<?> source, Runnable registration) {
        try { registration.run(); }
        catch (RuntimeException ex) { throw new IllegalArgumentException(source.path() + ": " + ex.getMessage(), ex); }
    }
    private static <T> void add(Map<ResourceLocation, Located<T>> map, ResourceLocation id, Located<T> source) {
        var old = map.putIfAbsent(id, source);
        if (old != null) throw new IllegalArgumentException("Duplicate definition " + id + ": " + old.path() + " and " + source.path());
    }
    private static <T> void mergeInto(Map<ResourceLocation, Located<T>> target, Map<ResourceLocation, Located<T>> source) {
        source.forEach((id, value) -> add(target, id, value));
    }
    private static <T> Map<ResourceLocation, T> values(Map<ResourceLocation, Located<T>> sources) {
        var result = new TreeMap<ResourceLocation, T>(); sources.forEach((id, source) -> result.put(id, source.value()));
        return immutableSorted(result);
    }
    private static <T> Map<ResourceLocation, T> immutableSorted(Map<ResourceLocation, T> values) {
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }
    private static void validateConcreteIds(MaterialFactory.Result result, Path source,
            Map<String, Map<ResourceLocation, Path>> registries) {
        var plan = result.content();
        plan.itemDefinitions().keySet().forEach(id -> unique(registries, "item", id, source));
        plan.blockDefinitions().keySet().forEach(id -> unique(registries, "block", id, source));
        plan.entities().values().forEach(entity -> unique(registries, "entity", entity.name(), source));
        plan.blockEntityDefinitions().keySet().forEach(id -> unique(registries, "block_entity", id, source));
        if (plan.fluid() != null) unique(registries, "fluid", plan.fluid().name(), source);
    }
    private static void unique(Map<String, Map<ResourceLocation, Path>> registries, String registry, ResourceLocation id, Path source) {
        var previous = registries.computeIfAbsent(registry, key -> new HashMap<>()).putIfAbsent(id, source);
        if (previous != null) throw new IllegalArgumentException("Duplicate " + registry + " ID " + id + ": " + previous + " and " + source);
    }
}
