package zone.moddev.mc.lib.common.builders;

import zone.moddev.mc.lib.common.modules.ModuleCatalog;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.common.modules.*;
import zone.moddev.mc.lib.common.types.MaterialPart;
import java.io.StringReader;
import java.nio.file.*;
import java.util.Map;

public final class DataModulesTest {
    public static void main(String[] args) throws Exception {
        var bundled = ModuleCatalog.bundled();
        require(bundled.items().size() == 32 && bundled.blocks().size() == 23 && bundled.fluids().size() == 1);
        bundled.items().values().forEach(v -> roundtrip(DataModules.Item.CODEC, v));
        bundled.blocks().values().forEach(v -> roundtrip(DataModules.Block.CODEC, v));
        bundled.fluids().values().forEach(v -> roundtrip(DataModules.Fluid.CODEC, v));
        var api = new MaterialExtensions();
        var factory = new MaterialFactory(api, mod -> false);
        var baseline = factory.load(id("test:copper"), new StringReader("{}" )).content();
        require(!baseline.items().containsKey(ModuleCatalog.bundled().parts().resolve("crystal").getOrThrow()));
        require(baseline.fluid().typeProperties().temperature() == 1300);
        require(baseline.blocks().get(ModuleCatalog.bundled().parts().resolve("stairs").getOrThrow()).tags().contains(id("minecraft:stairs")));
        var root = Files.createTempDirectory("mmdlib-modules");
        try {
            write(root, "data/example/item/parts/token.json", """
                {"factory":"mmdlib:basic_item","template":{"max_stack_size":32,"fire_resistant":true}}
                """);
            write(root, "data/example/block/panel.json", """
                {"factory":"mmdlib:plate","material_defaults":false,"template":{"properties":{"hardness":2}}}
                """);
            write(root, "data/example/fluid/coolant.json", """
                {"factory":"mmdlib:flowing_fluid","material_defaults":false,"template":{}}
                """);
            var external = ModuleCatalog.load(root);
            require(external.items().containsKey(id("example:parts/token")));
            external.install(api);
            var result = factory.load(id("test:custom"), new StringReader("""
                {"items":"example:parts/token","blocks":"example:panel","fluid":"example:coolant"}
                """ )).content();
            require(result.items().size() == 2 && result.blocks().size() == 1);
            require(result.extraItems().get(id("example:parts/token")).type().equals("mmdlib:basic_item"));
            require(result.extraBlocks().get(id("example:panel")).properties().hardness() == 2);
            var excluded = factory.load(id("test:excluded"), new StringReader("""
                {"items":"all !example:parts/token","blocks":"","has_fluid":false}
                """ )).content();
            require(excluded.extraItems().isEmpty());
            var override = DataModules.Item.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"factory":"mmdlib:basic_item","template":{"max_stack_size":16}}
                """ )).getOrThrow();
            var replacement = new ModuleCatalog(Map.of(id("mmdlib:ingot"), override), Map.of(), Map.of());
            try { replacement.install(api); throw new AssertionError("Duplicate accepted"); }
            catch (IllegalArgumentException expected) { }
            replacement.install(api, true);
            require(factory.load(id("test:override"), new StringReader("{}" )).content().items()
                    .get(ModuleCatalog.bundled().parts().resolve("ingot").getOrThrow()).maxStackSize() == 16);
            Path archive = root.resolve("modules.zip");
            try (var zip = FileSystems.newFileSystem(java.net.URI.create("jar:" + archive.toUri()), Map.of("create", "true"))) {
                write(zip.getPath("/"), "data/example/item/parts/token.json", Files.readString(root.resolve("data/example/item/parts/token.json")));
                require(ModuleCatalog.load(zip.getPath("/")).items().equals(external.items()));
            }
            write(root, "data/example/item/bad.json", "{\"factory\":17}");
            try { ModuleCatalog.load(root); throw new AssertionError("Malformed module accepted"); }
            catch (IllegalArgumentException ex) { require(ex.getMessage().contains("bad.json")); }
        } finally {
            try (var paths = Files.walk(root)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("Bundled and external data modules, overrides, selections, ZIP paths, and JSON/NBT codecs passed");
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static void write(Path root, String file, String data) throws Exception {
        var path = root.resolve(file); Files.createDirectories(path.getParent()); Files.writeString(path, data);
    }
    private static <T> void roundtrip(Codec<T> codec, T value) {
        require(codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow()).getOrThrow().equals(value));
        require(codec.parse(NbtOps.INSTANCE, codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow()).getOrThrow().equals(value));
    }
    private static void require(boolean condition) { if (!condition) throw new AssertionError("Data module assertion failed"); }
}
