package zone.moddev.mc.lib.common.builders;

import java.nio.file.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import zone.moddev.mc.lib.common.loading.DefinitionLoader;
import zone.moddev.mc.lib.common.types.CreativeTabDefinition;

public final class DefinitionLoaderTest {
    private static final AtomicInteger calls = new AtomicInteger();

    public static void handler(String context) {
        calls.incrementAndGet();
    }

    @Test
    void loadsDefinitionsAcrossMods() throws Exception {
        Path temporary = Files.createTempDirectory("mmdlib-loading");
        var loader = DefinitionLoaderTest.class.getClassLoader();
        try {
            var alpha = temporary.resolve("alpha/mmdlib");
            var beta = temporary.resolve("beta/mmdlib");
            write(alpha, "material/copper.json",
                """
                    {"color":-12345,"items":"all","blocks":"all","fluid":"beta:coolant",
                     "event":{"test:probe":"beta:effect"}}
                    """);
            write(beta, "item/components/gear.json", """
                {"factory":"beta:basic_item","template":{"max_stack_size":16}}
                """);
            write(beta, "item/optional.json",
                """
                    {"factory":"beta:basic_item","requires_mod":"optional_mod","template":{}}
                    """);
            write(beta, "block/machine.json",
                """
                    {"factory":"beta:machine","template":{},"part":{"block_entity":{"factory":"beta:machine_entity"}}}
                    """);
            write(beta, "fluid/coolant.json", """
                {"factory":"beta:fluid","template":{}}
                """);
            write(beta, "extension/effect.json",
                """
                    {"events":[{"type":"test:probe","handler":"zone.moddev.mc.lib.common.builders.DefinitionLoaderTest#handler",
                     "id":"beta:effect","name":"Loading probe"}]}
                    """);
            write(beta, "standalone/block/press.json", """
                {"module":"beta:machine","block_entity_data":{"speed":2}}
                """);
            write(beta, "standalone/item/tools/wrench.json",
                """
                    {"module":"beta:components/gear","template":{"max_stack_size":1}}
                    """);
            write(beta, "standalone/item/gated.json", """
                {"module":"beta:optional"}
                """);
            write(beta, "ignored/broken.json",
                "this is intentionally not JSON");
            write(beta, "item/README.txt", "not a definition");
            var sources = List.of(new SourceRoot("alpha", alpha, loader),
                new SourceRoot("beta", beta, loader),
                new SourceRoot("absent", temporary.resolve("missing"), loader));
            var api = api();
            var loaded = DefinitionLoader.load(sources, api, mod -> false);
            require(loaded.modules().items().size() == 2
                && loaded.modules().blocks().size() == 1
                && loaded.modules().fluids().size() == 1
                && loaded.extensions().size() == 1);
            require(loaded.materials().size() == 1);
            var copper = loaded.materials().get(id("alpha:copper"));
            require(copper.material().name().orElseThrow()
                .equals(id("alpha:copper")));
            var registry = api.snapshot().parts();
            require(copper.content().items()
                .get(registry.resolve("beta:components/gear").getOrThrow())
                .maxStackSize() == 16);
            require(!copper.content().items()
                .containsKey(registry.resolve("beta:optional").getOrThrow()));
            require(copper.content().blockEntities().size() == 1
                && copper.content().fluid() != null);
            require(copper.content().fluid().appearance().tint() == -12345);
            require(calls.get() == 0);
            api.events().dispatch(copper.material(),
                api.events().registerEvent("other", String.class), "ignored");
            api.events().dispatch(copper.material(),
                new zone.moddev.mc.lib.api.MaterialEvent<>("test:probe",
                    String.class),
                "loaded");
            require(calls.get() == 1);
            // Standalone definitions keep their file IDs, with no material
            // prefix.
            require(loaded.standalone().keySet()
                .equals(java.util.Set.of(id("beta:press"))));
            var press = loaded.standalone().get(id("beta:press")).content();
            require(press.blockDefinitions().containsKey(id("beta:press")));
            var pressEntity = press.blockEntityDefinitions()
                .get(id("beta:press"));
            require(pressEntity != null
                && pressEntity.block().equals(id("beta:press"))
                && pressEntity.factory().equals(id("beta:machine_entity"))
                && pressEntity.factoryData().json().getAsJsonObject()
                    .get("speed").getAsInt() == 2);
            require(loaded.standaloneItems().keySet()
                .equals(java.util.Set.of(id("beta:tools/wrench"))));
            var wrench = loaded.standaloneItems().get(id("beta:tools/wrench"))
                .content().itemDefinitions();
            require(wrench.keySet()
                .equals(java.util.Set.of(id("beta:tools/wrench")))
                && wrench.get(id("beta:tools/wrench")).maxStackSize() == 1);
            require(DefinitionLoader.load(sources, api(), mod -> true)
                .standaloneItems().containsKey(id("beta:gated")));
            var reversed = DefinitionLoader.load(
                List.of(sources.get(1), sources.get(0)), api(), mod -> false);
            require(reversed.materials().equals(loaded.materials()));
            try {
                loaded.materials().clear();
                throw new AssertionError("Mutable results");
            } catch (UnsupportedOperationException expected) {
            }
            var enabled = DefinitionLoader.load(sources, api(), mod -> true);
            require(enabled.materials().get(id("alpha:copper")).content()
                .items()
                .containsKey(registry.resolve("beta:optional").getOrThrow()));
            var duplicate = temporary.resolve("duplicate/mmdlib");
            write(duplicate, "item/components/gear.json",
                Files.readString(beta.resolve("item/components/gear.json")));
            var clean = api();
            expectFailure(
                () -> DefinitionLoader.load(
                    List.of(sources.get(1),
                        new SourceRoot("beta", duplicate, loader)),
                    clean, mod -> false),
                "Duplicate definition", "components/gear.json");
            require(clean.snapshot().items().isEmpty());
            var broken = temporary.resolve("broken/mmdlib");
            write(broken, "item/bad.json", "{\"factory\":17}");
            expectFailure(() -> DefinitionLoader.load(
                List.of(new SourceRoot("bad", broken, loader)), api(),
                mod -> false), "bad.json");
            Files.delete(broken.resolve("item/bad.json"));
            write(broken, "material/bad.json",
                "{\"items\":\"missing:part\",\"has_fluid\":false}");
            expectFailure(() -> DefinitionLoader.load(
                List.of(new SourceRoot("bad", broken, loader)), api(),
                mod -> false), "bad.json", "missing:part");
            Files.delete(broken.resolve("material/bad.json"));
            write(broken, "extension/bad.json",
                """
                    {"events":[{"type":"test:probe","handler":"missing.Class#method","id":"bad:handler"}]}
                    """);
            expectFailure(() -> DefinitionLoader.load(
                List.of(new SourceRoot("bad", broken, loader)), api(),
                mod -> false), "bad.json", "bad:handler");
            write(broken, "item/also_bad.json", "{\"factory\":17}");
            expectFailure(() -> DefinitionLoader.load(
                List.of(new SourceRoot("bad", broken, loader)), api(),
                mod -> false), "Cannot resolve", "bad:handler");
            var plain = temporary.resolve("plain/mmdlib");
            write(plain, "block/slab.json",
                "{\"factory\":\"plain:slab\",\"template\":{}}");
            write(plain, "standalone/block/odd.json",
                "{\"module\":\"plain:slab\",\"block_entity_data\":{\"x\":1}}");
            expectFailure(() -> DefinitionLoader.load(
                List.of(new SourceRoot("plain", plain, loader)), api(),
                mod -> false), "odd.json", "block_entity_data");
            Files.delete(plain.resolve("standalone/block/odd.json"));
            write(plain, "standalone/item/stray.json",
                "{\"module\":\"plain:missing\"}");
            expectFailure(() -> DefinitionLoader.load(
                List.of(new SourceRoot("plain", plain, loader)), api(),
                mod -> false), "stray.json", "Unknown item module");
            // Different material/part combinations can produce the same
            // generated item ID.
            var collisions = temporary.resolve("collisions/mmdlib");
            for (String part : List.of("a", "b_a"))
                write(collisions, "item/" + part + ".json",
                    "{\"factory\":\"test:basic\",\"template\":{}}");
            write(collisions, "material/x_b.json",
                "{\"items\":\"test:a\",\"has_fluid\":false}");
            write(collisions, "material/x.json",
                "{\"items\":\"test:b_a\",\"has_fluid\":false}");
            expectFailure(() -> DefinitionLoader.load(
                List.of(new SourceRoot("test", collisions, loader)), api(),
                mod -> false), "Duplicate item ID", "test:x_b_a");
            // Mounted JAR/ZIP paths require no URL conversion or manually owned
            // mounts in the
            // loader.
            var archive = temporary.resolve("definitions.zip");
            try (var zip = FileSystems.newFileSystem(
                java.net.URI.create("jar:" + archive.toUri()),
                Map.of("create", "true"))) {
                var root = zip.getPath("/mmdlib");
                write(root, "item/gear.json",
                    "{\"factory\":\"zip:basic\",\"template\":{}}");
                write(root, "material/metal.json",
                    "{\"items\":\"zip:gear\",\"has_fluid\":false}");
                var zipped = DefinitionLoader.load(
                    List.of(new SourceRoot("zip", root, loader)), api(),
                    mod -> false);
                require(zipped.materials().containsKey(id("zip:metal")));
                require(zip.isOpen());
            }
            require(DefinitionLoader
                .load(List.of(), new MaterialExtensions(), mod -> false)
                .materials().isEmpty());
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (var path : paths
                    .sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        }
    }

    @Test
    void loadsSoundEvents() throws Exception {
        Path temporary = Files.createTempDirectory("mmdlib-sounds");
        var loader = DefinitionLoaderTest.class.getClassLoader();
        try {
            var alpha = temporary.resolve("alpha/mmdlib");
            var beta = temporary.resolve("beta/mmdlib");
            write(alpha, "sound/machine/hum.json", "{\"range\": 24}");
            write(beta, "sound/click.json", "{}");
            var loaded = DefinitionLoader
                .load(
                    List.of(new SourceRoot("alpha", alpha, loader),
                        new SourceRoot("beta", beta, loader)),
                    api(), mod -> false);
            require(loaded.sounds().keySet().equals(
                java.util.Set.of(id("alpha:machine/hum"), id("beta:click"))));
            require(loaded.sounds().get(id("alpha:machine/hum")).range()
                .equals(java.util.Optional.of(24f)));
            require(loaded.sounds().get(id("beta:click")).range().isEmpty());

            var bad = temporary.resolve("bad/mmdlib");
            write(bad, "sound/silent.json", "{\"range\": 0}");
            expectFailure(() -> DefinitionLoader.load(
                List.of(new SourceRoot("bad", bad, loader)), api(),
                mod -> false), "silent.json");

            write(alpha, "sound_type/steel.json", """
                {"base":"metal","place":"alpha:machine/hum","volume":0.5}
                """);
            var withSets = DefinitionLoader.load(
                List.of(new SourceRoot("alpha", alpha, loader)), api(),
                mod -> false);
            var steel = withSets.soundTypes().get(id("alpha:steel"));
            require(steel.base().equals("metal")
                && steel.place()
                    .equals(java.util.Optional.of(id("alpha:machine/hum")))
                && steel.breakSound().isEmpty()
                && steel.volume().equals(java.util.Optional.of(.5f)));
            var chained = temporary.resolve("chained/mmdlib");
            write(chained, "sound_type/loop.json",
                "{\"base\": \"alpha:steel\"}");
            expectFailure(() -> DefinitionLoader.load(
                List.of(new SourceRoot("chained", chained, loader)), api(),
                mod -> false), "loop.json", "vanilla sound set name");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (var path : paths
                    .sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        }
    }

    @Test
    void loadsCreativeTabs() throws Exception {
        Path temporary = Files.createTempDirectory("mmdlib-tabs");
        var loader = DefinitionLoaderTest.class.getClassLoader();
        try {
            var root = temporary.resolve("alpha/mmdlib");
            write(root, "creative_tab/tools.json",
                """
                    {"title": "itemGroup.alpha.tools", "title_fallback": "Tools",
                     "icon": "alpha:hammer",
                     "contents": ["alpha:hammer", "#c:ingots", {"material": "alpha:copper"}],
                     "before": ["minecraft:combat"], "after": ["minecraft:tools_and_utilities"],
                     "search_bar": true}
                    """);
            write(root, "creative_tab/misc/odds.json",
                "{\"contents\": [{\"material\": \"alpha:tin\"}]}");
            var tabs = DefinitionLoader
                .load(List.of(new SourceRoot("alpha", root, loader)), api(),
                    mod -> false)
                .creativeTabs();
            var tools = tabs.get(id("alpha:tools"));
            var built = CreativeTabDefinition.builder()
                .title("itemGroup.alpha.tools", "Tools")
                .icon(id("alpha:hammer")).item(id("alpha:hammer"))
                .tag(id("c:ingots")).material(id("alpha:copper"))
                .before(id("minecraft:combat"))
                .after(id("minecraft:tools_and_utilities")).searchBar().build();
            require(tools.equals(built));
            var odds = tabs.get(id("alpha:misc/odds"));
            require(odds.titleKey(id("alpha:misc/odds"))
                .equals("itemGroup.alpha.misc.odds") && odds.icon().isEmpty()
                && !odds.searchBar() && odds.before().isEmpty());

            var bad = temporary.resolve("bad/mmdlib");
            write(bad, "creative_tab/tags.json",
                "{\"contents\": [\"#c:ingots\"]}");
            expectFailure(() -> DefinitionLoader.load(
                List.of(new SourceRoot("bad", bad, loader)), api(),
                mod -> false), "tags.json", "needs an icon");
            var worse = temporary.resolve("worse/mmdlib");
            write(worse, "creative_tab/broken.json",
                "{\"contents\": [\"Not An Item\"]}");
            expectFailure(() -> DefinitionLoader.load(
                List.of(new SourceRoot("worse", worse, loader)), api(),
                mod -> false), "broken.json");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (var path : paths
                    .sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        }
    }

    @Test
    void sourcesReplaceSoundsAndSoundSets() throws Exception {
        Path temporary = Files.createTempDirectory("mmdlib-source-sounds");
        var loader = DefinitionLoaderTest.class.getClassLoader();
        try {
            var root = temporary.resolve("alpha/mmdlib");
            write(root, "sound/hum.json", "{}");
            write(root, "sound_type/steel.json", "{\"base\": \"metal\"}");
            var louder = new zone.moddev.mc.lib.common.types.SoundDefinition(
                java.util.Optional.of(40f));
            var wooden = new zone.moddev.mc.lib.common.types.SoundTypeDefinition(
                "wood", java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty());
            var seen = new java.util.ArrayList<ResourceLocation>();
            var api = api();
            api.registerDefinitionSource("scripts", catalog -> {
                seen.addAll(catalog.soundFiles().keySet());
                seen.addAll(catalog.soundTypeFiles().keySet());
                return new MaterialExtensions.Definitions(List.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(id("alpha:hum"), louder), Map.of(
                        id("alpha:steel"), wooden, id("mmdlib:new"), wooden));
            });
            var loaded = DefinitionLoader.load(
                List.of(new SourceRoot("alpha", root, loader)), api,
                mod -> false);
            require(seen.equals(List.of(id("alpha:hum"), id("alpha:steel"))));
            require(loaded.sounds().get(id("alpha:hum")).equals(louder));
            require(loaded.soundTypes().get(id("alpha:steel")).equals(wooden)
                && loaded.soundTypes().containsKey(id("mmdlib:new")));
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (var path : paths
                    .sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        }
    }

    @Test
    void sourcesReplaceAndAddCreativeTabs() throws Exception {
        Path temporary = Files.createTempDirectory("mmdlib-source-tabs");
        var loader = DefinitionLoaderTest.class.getClassLoader();
        try {
            var root = temporary.resolve("alpha/mmdlib");
            write(root, "creative_tab/tools.json",
                "{\"contents\": [\"alpha:hammer\"]}");
            var sources = List.of(new SourceRoot("alpha", root, loader));
            var changed = CreativeTabDefinition.builder()
                .item(id("alpha:hammer")).item(id("minecraft:apple")).build();
            var added = CreativeTabDefinition.builder()
                .item(id("minecraft:stick")).build();
            var seen = new java.util.ArrayList<ResourceLocation>();
            var api = api();
            api.registerDefinitionSource("scripts", catalog -> {
                // The file's tab is visible before any source returns.
                seen.addAll(catalog.creativeTabFiles().keySet());
                return new MaterialExtensions.Definitions(List.of(), Map.of(),
                    Map.of(), Map.of(id("alpha:tools"), changed,
                        id("mmdlib:extra"), added));
            });
            var tabs = DefinitionLoader.load(sources, api, mod -> false)
                .creativeTabs();
            require(seen.equals(List.of(id("alpha:tools"))));
            require(tabs.get(id("alpha:tools")).equals(changed)
                && tabs.get(id("mmdlib:extra")).equals(added)
                && tabs.size() == 2);

            var twice = api();
            for (var name : List.of("first", "second"))
                twice.registerDefinitionSource(name,
                    catalog -> new MaterialExtensions.Definitions(List.of(),
                        Map.of(), Map.of(), Map.of(id("mmdlib:extra"), added)));
            expectFailure(
                () -> DefinitionLoader.load(sources, twice, mod -> false),
                "Duplicate creative tab mmdlib:extra", "first", "second");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (var path : paths
                    .sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        }
    }

    @Test
    void plansCodeMaterialSources() throws Exception {
        Path temporary = Files.createTempDirectory("mmdlib-sources");
        var loader = DefinitionLoaderTest.class.getClassLoader();
        try {
            var root = temporary.resolve("alpha/mmdlib");
            write(root, "material/copper.json",
                "{\"items\":\"\",\"blocks\":\"\",\"has_fluid\":false}");
            var sources = List.of(new SourceRoot("alpha", root, loader));

            var api = api();
            api.registerDefinitionSource("scripts",
                materials(codeMaterial("alpha:tin")));
            var loaded = DefinitionLoader.load(sources, api, mod -> false);
            require(loaded.materials().keySet()
                .equals(java.util.Set.of(id("alpha:copper"), id("alpha:tin"))));

            // A source's module is installed before material files decode, so
            // files can select it.
            write(root, "material/stone.json",
                "{\"items\":\"\",\"blocks\":\"alpha:pillar\",\"has_fluid\":false}");
            MaterialExtensions.DefinitionSource pillarSource = catalog -> {
                catalog.registerBlockModule(id("alpha:pillar"),
                    zone.moddev.mc.lib.common.modules.DataModules.Block.CODEC
                        .parse(com.mojang.serialization.JsonOps.INSTANCE,
                            com.google.gson.JsonParser.parseString(
                                "{\"factory\":\"alpha:pillar_block\",\"template\":{}}"))
                        .getOrThrow());
                catalog.registerItemModule(id("alpha:token"),
                    zone.moddev.mc.lib.common.modules.DataModules.Item.CODEC
                        .parse(com.mojang.serialization.JsonOps.INSTANCE,
                            com.google.gson.JsonParser.parseString(
                                "{\"factory\":\"alpha:basic\",\"template\":{}}"))
                        .getOrThrow());
                return new MaterialExtensions.Definitions(List.of(), Map.of(
                    id("alpha:marble"),
                    new zone.moddev.mc.lib.common.types.StandaloneBlock(
                        id("alpha:pillar"),
                        zone.moddev.mc.lib.common.types.FactoryData.EMPTY,
                        java.util.Optional.empty(),
                        zone.moddev.mc.lib.common.types.MaterialRuntimeSettings.DEFAULT)),
                    Map.of(id("alpha:coin"),
                        new zone.moddev.mc.lib.common.types.StandaloneItem(
                            id("alpha:token"),
                            zone.moddev.mc.lib.common.types.FactoryData.EMPTY,
                            java.util.Optional.empty(),
                            zone.moddev.mc.lib.common.types.MaterialRuntimeSettings.DEFAULT)));
            };
            var withModule = api();
            withModule.registerDefinitionSource("scripts", pillarSource);
            var moduled = DefinitionLoader.load(sources, withModule,
                mod -> false);
            require(moduled.materials().get(id("alpha:stone")).content()
                .blockDefinitions().containsKey(id("alpha:stone_pillar")));
            require(moduled.standalone().get(id("alpha:marble")).content()
                .blockDefinitions().containsKey(id("alpha:marble")));
            require(moduled.standaloneItems().get(id("alpha:coin")).content()
                .itemDefinitions().containsKey(id("alpha:coin")));
            write(root, "standalone/block/marble.json",
                "{\"module\":\"alpha:pillar\"}");
            var again = api();
            again.registerDefinitionSource("scripts", pillarSource);
            expectFailure(
                () -> DefinitionLoader.load(sources, again, mod -> false),
                "Duplicate standalone block alpha:marble",
                "definition source 'scripts'");
            Files.delete(root.resolve("standalone/block/marble.json"));
            Files.delete(root.resolve("material/stone.json"));

            var clash = api();
            clash.registerDefinitionSource("scripts",
                materials(codeMaterial("alpha:copper")));
            expectFailure(
                () -> DefinitionLoader.load(sources, clash, mod -> false),
                "Duplicate material alpha:copper",
                "alpha/mmdlib/material/copper.json",
                "definition source 'scripts'");

            var twice = api();
            twice.registerDefinitionSource("scripts", materials(
                codeMaterial("alpha:tin"), codeMaterial("alpha:tin")));
            expectFailure(
                () -> DefinitionLoader.load(List.of(), twice, mod -> false),
                "Duplicate material alpha:tin");

            var unnamed = api();
            unnamed.registerDefinitionSource("scripts",
                materials(new zone.moddev.mc.lib.common.types.MMDMaterial(
                    java.util.Optional.empty(), java.util.Optional.empty(),
                    zone.moddev.mc.lib.common.types.MaterialProperties.DEFAULT,
                    nothing(), Map.of(),
                    zone.moddev.mc.lib.common.types.MaterialRuntimeSettings.DEFAULT)));
            expectFailure(
                () -> DefinitionLoader.load(List.of(), unnamed, mod -> false),
                "definition source 'scripts'", "unnamed material");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (var path : paths
                    .sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        }
    }

    private static MaterialExtensions.DefinitionSource materials(
        zone.moddev.mc.lib.common.types.MMDMaterial... materials) {
        return api -> new MaterialExtensions.Definitions(List.of(materials),
            Map.of());
    }

    private static zone.moddev.mc.lib.common.types.MMDMaterial codeMaterial(
        String name) {
        return MaterialBuilder.create().setName(name).setSelection(nothing())
            .build();
    }

    private static zone.moddev.mc.lib.common.types.MaterialSelection nothing() {
        return new zone.moddev.mc.lib.common.types.MaterialSelection(
            java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), false);
    }

    private static MaterialExtensions api() {
        var api = new MaterialExtensions(false);
        api.events().registerEvent("test:probe", String.class);
        return api;
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private static void write(Path root, String file, String data)
        throws Exception {
        var path = root.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, data);
    }

    private static void require(boolean condition) {
        if (!condition)
            throw new AssertionError("Definition loader assertion failed");
    }

    private static void expectFailure(Runnable action, String... fragments) {
        try {
            action.run();
        } catch (IllegalArgumentException ex) {
            // Messages name file-system paths, which use backslashes on
            // Windows.
            var message = ex.getMessage().replace('\\', '/');
            for (var fragment : fragments)
                require(message.contains(fragment));
            return;
        }
        throw new AssertionError("Bad definitions accepted");
    }
}
