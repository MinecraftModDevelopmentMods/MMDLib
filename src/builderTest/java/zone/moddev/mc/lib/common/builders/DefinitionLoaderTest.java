package zone.moddev.mc.lib.common.builders;

import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.loading.DefinitionLoader;
import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class DefinitionLoaderTest {
    private static final AtomicInteger calls = new AtomicInteger();
    public static void handler(String context) { calls.incrementAndGet(); }
    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("mmdlib-loading");
        var loader = DefinitionLoaderTest.class.getClassLoader();
        try {
            var alpha = temporary.resolve("alpha/mmdlib");
            var beta = temporary.resolve("beta/mmdlib");
            write(alpha, "material/copper.json", """
                    {"color":-12345,"items":"all","blocks":"all","fluid":"beta:coolant",
                     "event":{"test:probe":"beta:effect"}}
                    """);
            write(beta, "item/components/gear.json", """
                    {"factory":"beta:basic_item","template":{"max_stack_size":16}}
                    """);
            write(beta, "item/optional.json", """
                    {"factory":"beta:basic_item","requires_mod":"optional_mod","template":{}}
                    """);
            write(beta, "block/machine.json", """
                    {"factory":"beta:machine","template":{},"part":{"block_entity":{"factory":"beta:machine_entity"}}}
                    """);
            write(beta, "fluid/coolant.json", """
                    {"factory":"beta:fluid","template":{}}
                    """);
            write(beta, "extension/effect.json", """
                    {"events":[{"type":"test:probe","handler":"zone.moddev.mc.lib.common.builders.DefinitionLoaderTest#handler",
                     "id":"beta:effect","name":"Loading probe"}]}
                    """);
            write(beta, "ignored/broken.json", "this is intentionally not JSON");
            write(beta, "item/README.txt", "not a definition");
            var sources = List.of(new SourceRoot("alpha", alpha, loader), new SourceRoot("beta", beta, loader),
                    new SourceRoot("absent", temporary.resolve("missing"), loader));
            var api = api();
            var loaded = DefinitionLoader.load(sources, api, mod -> false);
            require(loaded.modules().items().size() == 2 && loaded.modules().blocks().size() == 1
                    && loaded.modules().fluids().size() == 1 && loaded.extensions().size() == 1);
            require(loaded.materials().size() == 1);
            var copper = loaded.materials().get(id("alpha:copper"));
            require(copper.material().name().orElseThrow().equals(id("alpha:copper")));
            var registry = api.snapshot().parts();
            require(copper.content().items().get(registry.resolve("beta:components/gear").getOrThrow()).maxStackSize() == 16);
            require(!copper.content().items().containsKey(registry.resolve("beta:optional").getOrThrow()));
            require(copper.content().blockEntities().size() == 1 && copper.content().fluid() != null);
            require(copper.content().fluid().appearance().tint() == -12345);
            require(calls.get() == 0);
            api.events().dispatch(copper.material(), api.events().registerEvent("other", String.class), "ignored");
            api.events().dispatch(copper.material(), new zone.moddev.mc.lib.api.MaterialEvent<>("test:probe", String.class), "loaded");
            require(calls.get() == 1);
            var reversed = DefinitionLoader.load(List.of(sources.get(1), sources.get(0)), api(), mod -> false);
            require(reversed.materials().equals(loaded.materials()));
            try { loaded.materials().clear(); throw new AssertionError("Mutable results"); }
            catch (UnsupportedOperationException expected) { }
            var enabled = DefinitionLoader.load(sources, api(), mod -> true);
            require(enabled.materials().get(id("alpha:copper")).content().items().containsKey(registry.resolve("beta:optional").getOrThrow()));
            var duplicate = temporary.resolve("duplicate/mmdlib");
            write(duplicate, "item/components/gear.json", Files.readString(beta.resolve("item/components/gear.json")));
            var clean = api();
            expectFailure(() -> DefinitionLoader.load(List.of(sources.get(1), new SourceRoot("beta", duplicate, loader)), clean, mod -> false), "Duplicate definition", "components/gear.json");
            require(clean.snapshot().items().isEmpty());
            var broken = temporary.resolve("broken/mmdlib");
            write(broken, "item/bad.json", "{\"factory\":17}");
            expectFailure(() -> DefinitionLoader.load(List.of(new SourceRoot("bad", broken, loader)), api(), mod -> false), "bad.json");
            Files.delete(broken.resolve("item/bad.json"));
            write(broken, "material/bad.json", "{\"items\":\"missing:part\",\"has_fluid\":false}");
            expectFailure(() -> DefinitionLoader.load(List.of(new SourceRoot("bad", broken, loader)), api(), mod -> false), "bad.json", "missing:part");
            Files.delete(broken.resolve("material/bad.json"));
            write(broken, "extension/bad.json", """
                    {"events":[{"type":"test:probe","handler":"missing.Class#method","id":"bad:handler"}]}
                    """);
            expectFailure(() -> DefinitionLoader.load(List.of(new SourceRoot("bad", broken, loader)), api(), mod -> false), "bad.json", "bad:handler");
            write(broken, "item/also_bad.json", "{\"factory\":17}");
            expectFailure(() -> DefinitionLoader.load(List.of(new SourceRoot("bad", broken, loader)), api(), mod -> false), "Cannot resolve", "bad:handler");
            // Different material/part combinations can produce the same generated item ID.
            var collisions = temporary.resolve("collisions/mmdlib");
            for (String part : List.of("a", "b_a")) write(collisions, "item/" + part + ".json", "{\"factory\":\"test:basic\",\"template\":{}}");
            write(collisions, "material/x_b.json", "{\"items\":\"test:a\",\"has_fluid\":false}");
            write(collisions, "material/x.json", "{\"items\":\"test:b_a\",\"has_fluid\":false}");
            expectFailure(() -> DefinitionLoader.load(List.of(new SourceRoot("test", collisions, loader)), api(), mod -> false), "Duplicate item ID", "test:x_b_a");
            // Mounted JAR/ZIP paths require no URL conversion or manually owned mounts in the loader.
            var archive = temporary.resolve("definitions.zip");
            try (var zip = FileSystems.newFileSystem(java.net.URI.create("jar:" + archive.toUri()), Map.of("create", "true"))) {
                var root = zip.getPath("/mmdlib");
                write(root, "item/gear.json", "{\"factory\":\"zip:basic\",\"template\":{}}");
                write(root, "material/metal.json", "{\"items\":\"zip:gear\",\"has_fluid\":false}");
                var zipped = DefinitionLoader.load(List.of(new SourceRoot("zip", root, loader)), api(), mod -> false);
                require(zipped.materials().containsKey(id("zip:metal")));
                require(zip.isOpen());
            }
            require(DefinitionLoader.load(List.of(), new MaterialExtensions(), mod -> false).materials().isEmpty());
            System.out.println("Folder dispatch, cross-mod staging, extension handles, IDs, duplicates, optional mods, and mounted ZIP loading passed");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
    private static MaterialExtensions api() {
        var api = new MaterialExtensions(false); api.events().registerEvent("test:probe", String.class); return api;
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static void write(Path root, String file, String data) throws Exception {
        var path = root.resolve(file); Files.createDirectories(path.getParent()); Files.writeString(path, data);
    }
    private static void require(boolean condition) { if (!condition) throw new AssertionError("Definition loader assertion failed"); }
    private static void expectFailure(Runnable action, String... fragments) {
        try { action.run(); } catch (IllegalArgumentException ex) {
            for (var fragment : fragments) require(ex.getMessage().contains(fragment)); return;
        }
        throw new AssertionError("Bad definitions accepted");
    }
}
