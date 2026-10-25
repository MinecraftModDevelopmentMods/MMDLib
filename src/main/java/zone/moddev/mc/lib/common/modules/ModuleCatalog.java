package zone.moddev.mc.lib.common.modules;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.types.MaterialPart;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** The same codecs are used for library resources, mod resources, and extracted pack directories. */
public record ModuleCatalog(Map<ResourceLocation, DataModules.Item> items,
        Map<ResourceLocation, DataModules.Block> blocks, Map<ResourceLocation, DataModules.Fluid> fluids) {
    public ModuleCatalog { items = Map.copyOf(items); blocks = Map.copyOf(blocks); fluids = Map.copyOf(fluids); }
    public zone.moddev.mc.lib.common.types.PartRegistry parts() {
        var result = new java.util.ArrayList<MaterialPart>();
        items.forEach((id, module) -> result.add(module.definition(id)));
        blocks.forEach((id, module) -> result.add(module.definition(id)));
        return new zone.moddev.mc.lib.common.types.PartRegistry(result);
    }
    public static ModuleCatalog bundled() { return Bundled.VALUE; }
    private static final class Bundled {
        private static final ModuleCatalog VALUE = loadBundled();
    }
    private static ModuleCatalog loadBundled() {
        var items = new java.util.LinkedHashMap<ResourceLocation, DataModules.Item>();
        var blocks = new java.util.LinkedHashMap<ResourceLocation, DataModules.Block>();
        var fluids = new java.util.LinkedHashMap<ResourceLocation, DataModules.Fluid>();
        try (var stream = ModuleCatalog.class.getResourceAsStream("/data/mmdlib/modules.json")) {
            if (stream == null) throw new IllegalStateException("Missing bundled module manifest");
            var manifest = Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))).getOrThrow();
            for (var entry : manifest.getOrDefault("items", java.util.List.of())) {
                var name = entry; items.put(ResourceLocation.fromNamespaceAndPath("mmdlib", name), resource("item/" + name, DataModules.Item.CODEC));
            }
            for (var entry : manifest.getOrDefault("blocks", java.util.List.of())) {
                var name = entry; blocks.put(ResourceLocation.fromNamespaceAndPath("mmdlib", name), resource("block/" + name, DataModules.Block.CODEC));
            }
            for (var name : manifest.getOrDefault("fluids", java.util.List.of()))
                fluids.put(ResourceLocation.fromNamespaceAndPath("mmdlib", name), resource("fluid/" + name, DataModules.Fluid.CODEC));
        } catch (IOException ex) { throw new java.io.UncheckedIOException(ex); }
        return new ModuleCatalog(items, blocks, fluids);
    }
    private static <T> T resource(String path, Codec<T> codec) {
        String file = "/data/mmdlib/" + path + ".json";
        var stream = ModuleCatalog.class.getResourceAsStream(file);
        if (stream == null) throw new IllegalStateException("Missing built-in module: " + file);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) { return read(file, reader, codec); }
        catch (IOException ex) { throw new java.io.UncheckedIOException(ex); }
    }
    private static <T> T read(String source, Reader reader, Codec<T> codec) {
        try { return codec.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow(); }
        catch (RuntimeException ex) { throw new IllegalArgumentException(source + ": " + ex.getMessage(), ex); }
    }
    /** packRoot contains data/<namespace>/{item,block,fluid}; works with mounted ZIP paths too. */
    public static ModuleCatalog load(Path packRoot) throws IOException {
        var items = new java.util.LinkedHashMap<ResourceLocation, DataModules.Item>();
        var blocks = new java.util.LinkedHashMap<ResourceLocation, DataModules.Block>();
        var fluids = new java.util.LinkedHashMap<ResourceLocation, DataModules.Fluid>();
        Path data = packRoot.resolve("data");
        if (!Files.exists(data)) return new ModuleCatalog(items, blocks, fluids);
        try (var namespaces = Files.list(data)) {
            for (Path ns : namespaces.filter(Files::isDirectory).sorted().toList()) {
                String name = ns.getFileName().toString();
                loadSection(ns.resolve("item"), name, DataModules.Item.CODEC, items);
                loadSection(ns.resolve("block"), name, DataModules.Block.CODEC, blocks);
                loadSection(ns.resolve("fluid"), name, DataModules.Fluid.CODEC, fluids);
            }
        }
        return new ModuleCatalog(items, blocks, fluids);
    }
    private static <T> void loadSection(Path directory, String namespace, Codec<T> codec, Map<ResourceLocation,T> result) throws IOException {
        if (!Files.exists(directory)) return;
        try (var files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String path = directory.relativize(file).toString().replace('\\','/');
                var id = ResourceLocation.fromNamespaceAndPath(namespace, path.substring(0, path.length()-5));
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.put(id, read(file.toString(), reader, codec)); }
            }
        }
    }
    public void install(MaterialExtensions extensions) { install(extensions, false); }
    public void install(MaterialExtensions extensions, boolean replaceExisting) {
        items.forEach((id, value) -> extensions.registerItemModule(id, value, replaceExisting));
        blocks.forEach((id, value) -> extensions.registerBlockModule(id, value, replaceExisting));
        fluids.forEach((id, value) -> extensions.registerFluidModule(id, value, replaceExisting));
    }
}
