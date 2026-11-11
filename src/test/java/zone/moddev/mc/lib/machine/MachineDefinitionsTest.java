package zone.moddev.mc.lib.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.definition.MachineDefinitions;
import zone.moddev.mc.lib.machine.recipe.generation.GeneratorProperties;

class MachineDefinitionsTest {
    @TempDir
    Path directory;

    private List<SourceRoot> sources() {
        return List.of(
            new SourceRoot("mmdlib", Path.of("src/main/resources/mmdlib"),
                getClass().getClassLoader()),
            new SourceRoot("test", directory, getClass().getClassLoader()));
    }

    private void write(String text) throws Exception {
        Files.createDirectories(directory.resolve("machine"));
        Files.writeString(directory.resolve("machine/crusher.xml"), text);
    }

    private String crusher() throws Exception {
        return Files.readString(Path
            .of("src/companion/resources/mmdlib/machine/simple_crusher.xml"));
    }

    @Test
    void originalConstructorRetainsAutomaticCubeDefaults() throws Exception {
        write(crusher());
        var d = MachineDefinitions.load(sources())
            .get(ResourceLocation.parse("test:crusher"));
        var compatible = new MachineDefinition(d.id(), d.checksum(), d.name(),
            d.hardness(), d.mineable(), d.requiresTool(), d.textures(),
            d.power(), d.fluids(), d.itemStorageId(), d.slots(), d.groups(),
            d.recipeType(), d.processor(), d.processorName(), d.redstone(),
            d.screen());
        assertEquals(d, compatible);
        assertNull(compatible.baseModel());
        assertNull(compatible.recipeSelection());
        assertTrue(compatible.ghosts().isEmpty());
    }

    @Test
    void newMetadataDoesNotReserveExistingStorageNames() throws Exception {
        for (var key : List.of("multiblock", "multiblock_view",
            "mmdlib_ghost_items")) {
            write(crusher().replace("<items id=\"items\">",
                "<items id=\"" + key + "\">"));
            assertEquals(key, MachineDefinitions.load(sources())
                .get(ResourceLocation.parse("test:crusher")).itemStorageId());
            write(crusher().replace("<items id=\"items\">", "<fluids id=\""
                + key
                + "\"><tank id=\"water\" role=\"output\" capacity=\"1000\"/></fluids><items id=\"items\">"));
            assertEquals(key, MachineDefinitions.load(sources())
                .get(ResourceLocation.parse("test:crusher")).fluids().id());
        }
    }

    @Test
    void recipePresentationOnlyMarksGhostInputsSharedByItsMachines()
        throws Exception {
        write(crusher());
        var real = MachineDefinitions.load(sources())
            .get(ResourceLocation.parse("test:crusher"));
        write(crusher().replace("<items id=\"items\">",
            "<items id=\"items\"><slots id=\"pattern\" count=\"1\" role=\"input\" type=\"ghost\"/>"));
        var ghost = MachineDefinitions.load(sources()).get(real.id());
        var type = ghost.recipeType().id();
        assertEquals(Set.of("pattern"),
            MachineDefinition.ghostInputsFor(List.of(ghost), type));
        assertEquals(Set.of(),
            MachineDefinition.ghostInputsFor(List.of(ghost, real), type));
        assertEquals(Set.of(),
            MachineDefinition.ghostInputsFor(List.of(real), type));
        assertEquals(Set.of(), MachineDefinition.ghostInputsFor(List.of(ghost),
            ResourceLocation.parse("test:unknown")));
        assertEquals(Set.of(),
            MachineDefinition.ghostInputsFor(List.of(), type));
    }

    @Test
    void ghostInputsAreDeclaredUnderItemsWithoutAllocatingRealStorage()
        throws Exception {
        String ghost = "<slots id=\"pattern\" count=\"1\" role=\"input\" type=\"ghost\" default-item=\"minecraft:stone\"/>";
        write(crusher().replace("<items id=\"items\">",
            "<items id=\"items\">" + ghost));
        var machine = MachineDefinitions.load(sources())
            .get(ResourceLocation.parse("test:crusher"));
        assertEquals(3, machine.slots().size());
        assertEquals(1, machine.ghosts().size());
        assertEquals("pattern", machine.ghosts().getFirst().id());
        assertEquals(ResourceLocation.parse("minecraft:stone"),
            machine.ghosts().getFirst().defaultItem());
        var origin = new zone.moddev.mc.lib.gui.load.ScreenReader.Node(
            "placeholder",
            Map.of("generator", "mmdlib:resource-groups", "role", "input"),
            List.of(), "test", 1);
        var generated = zone.moddev.mc.lib.machine.definition.MachineScreenGenerators
            .generate(machine, origin);
        var inventory = generated.stream()
            .filter(node -> node.name().equals("inventory")).findFirst()
            .orElseThrow();
        assertEquals("pattern", inventory.attributes().get("storage"));
        assertEquals("ghost-slot", inventory.children().getFirst().name());
        for (String invalid : List.of(
            ghost.replace("role=\"input\"", "role=\"output\""),
            ghost.replace("type=\"ghost\"", "type=\"phantom\""),
            ghost.replace("count=\"1\"", "count=\"55\""),
            ghost.replace("id=\"pattern\"", "id=\"output\""),
            ghost.replace("type=\"ghost\"", "type=\"item\""))) {
            write(crusher().replace("<items id=\"items\">",
                "<items id=\"items\">" + invalid));
            assertThrows(IllegalArgumentException.class,
                () -> MachineDefinitions.load(sources()));
        }
        write(crusher().replace("<items id=\"items\">",
            "<items id=\"items\">" + ghost.replace("<slots", "<slot")));
        assertEquals(1, MachineDefinitions.load(sources()).get(machine.id())
            .ghosts().size());
    }

    @Test
    void selectableProcessorPreservesDefaultRecipeAndRejectsUnenabledDefaults()
        throws Exception {
        write(crusher().replace("<processor ",
            "<processor selectable=\"true\" default-recipe=\"test:stone\" "));
        var definition = MachineDefinitions.load(sources())
            .get(ResourceLocation.parse("test:crusher"));
        assertEquals(ResourceLocation.parse("test:stone"),
            definition.recipeSelection().defaultRecipe());
        write(crusher().replace("<processor ",
            "<processor selectable=\"true\" "));
        assertNull(MachineDefinitions.load(sources()).get(definition.id())
            .recipeSelection().defaultRecipe());
        for (String attributes : List.of("default-recipe=\"test:stone\"",
            "selectable=\"yes\"",
            "selectable=\"true\" default-recipe=\"Invalid:stone\"")) {
            write(crusher().replace("<processor ",
                "<processor " + attributes + " "));
            assertThrows(IllegalArgumentException.class,
                () -> MachineDefinitions.load(sources()));
        }
    }

    @Test
    void modelPresetUsesAuthoredBaseWithOrWithoutConditionalParts()
        throws Exception {
        for (String parts : List.of("",
            """
                <part id="full" model="test:block/full" when="$inventories.output.full"/>
                """)) {
            write(withAppearance(
                "<appearance preset=\"model\" model=\"test:block/casing\">"
                    + parts + "</appearance>"));
            var definition = MachineDefinitions.load(sources())
                .get(ResourceLocation.parse("test:crusher"));
            assertEquals(ResourceLocation.parse("test:block/casing"),
                definition.baseModel());
            assertTrue(definition.textures().isEmpty());
            assertTrue(definition.faceTextures().isEmpty());
            assertEquals(!parts.isEmpty(),
                definition.hasConditionalAppearance());
            var assets = zone.moddev.mc.lib.machine.neoforge.MachineResourcePack
                .build(net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                    Map.of(definition.id(), definition));
            var model = generatedModel(assets,
                "test:models/block/crusher.json");
            if (parts.isEmpty()) {
                assertEquals("test:block/casing",
                    model.get("parent").getAsString());
                assertFalse(model.has("loader"));
                assertFalse(assets.containsKey(ResourceLocation
                    .parse("test:models/block/crusher_base.json")));
            } else {
                assertEquals("mmdlib:conditional_machine",
                    model.get("loader").getAsString());
                assertEquals("test:block/crusher_base",
                    model.get("parent").getAsString());
                assertEquals("test:block/crusher_base",
                    model.getAsJsonObject("base").get("parent").getAsString());
                assertEquals("test:block/casing",
                    generatedModel(assets,
                        "test:models/block/crusher_base.json").get("parent")
                        .getAsString());
                assertEquals("test:block/full",
                    model.getAsJsonArray("parts").get(0).getAsJsonObject()
                        .getAsJsonObject("model").get("parent").getAsString());
                assertFalse(model.getAsJsonObject("textures").has("particle"));
                assertTrue(model.getAsJsonArray("face_textures").isEmpty());
            }
            assertEquals("test:block/crusher",
                generatedModel(assets, "test:models/item/crusher.json")
                    .get("parent").getAsString());
        }
    }

    @Test
    void modelPresetRejectsCubeSettingsFaceTexturesAndRecursiveModels()
        throws Exception {
        for (String appearance : List.of("<appearance preset=\"model\"/>",
            "<appearance preset=\"model\" model=\"Invalid:model\"/>",
            "<appearance preset=\"model\" model=\"test:block/crusher\"/>",
            "<appearance preset=\"model\" model=\"test:block/crusher_base\"/>",
            "<appearance preset=\"model\" model=\"test:block/casing\" front=\"test:block/front\"/>",
            """
                <appearance preset="model" model="test:block/casing">
                  <face-texture id="full" face="front" texture="test:block/full" when="$inventories.output.full"/>
                </appearance>
                """,
            "<appearance preset=\"cube-front\" model=\"test:block/casing\"/>",
            "<appearance preset=\"unknown\" model=\"test:block/casing\"/>")) {
            write(withAppearance(appearance));
            var error = assertThrows(IllegalArgumentException.class,
                () -> MachineDefinitions.load(sources()));
            assertTrue(error.getMessage().contains("crusher.xml"),
                error.getMessage());
        }
    }

    @Test
    void changingPresetDiscardsInheritedBaseAttributesButPreservesParts()
        throws Exception {
        write(
            """
                <machine xmlns="https://moddev.zone/mmdlib/machine" version="1" extends="test:base" name="Child">
                  <appearance preset="model" model="test:block/casing"/>
                </machine>
                """);
        Files.writeString(directory.resolve("machine/base.xml"),
            withAppearance(
                """
                    <appearance preset="cube-front" front="test:block/front" side="test:block/side" top="test:block/top">
                      <part id="full" model="test:block/full" when="$inventories.output.full"/>
                    </appearance>
                    """)
                .replace("<machine ", "<machine abstract=\"true\" "));
        var id = ResourceLocation.parse("test:crusher");
        var definition = MachineDefinitions.load(sources()).get(id);
        assertEquals(ResourceLocation.parse("test:block/casing"),
            definition.baseModel());
        assertTrue(definition.textures().isEmpty());
        assertEquals(1, definition.modelParts().size());
        String checksum = definition.checksum();
        write(Files.readString(directory.resolve("machine/crusher.xml"))
            .replace("block/casing", "block/other"));
        assertNotEquals(checksum,
            MachineDefinitions.load(sources()).get(id).checksum());
        Files.writeString(directory.resolve("machine/base.xml"),
            withAppearance("""
                <appearance preset="model" model="test:block/casing"/>
                """).replace("<machine ", "<machine abstract=\"true\" "));
        write(
            """
                <machine xmlns="https://moddev.zone/mmdlib/machine" version="1" extends="test:base" name="Child">
                  <appearance preset="cube-front" front="test:block/front" side="test:block/side" top="test:block/top"/>
                </machine>
                """);
        definition = MachineDefinitions.load(sources()).get(id);
        assertNull(definition.baseModel());
        assertEquals("test:block/side", definition.textures().get("bottom"));
    }

    private String withAppearance(String appearance) throws Exception {
        String original = crusher();
        int start = original.indexOf("<appearance");
        int end = original.indexOf("/>", start) + 2;
        return original.substring(0, start) + appearance
            + original.substring(end);
    }

    private com.google.gson.JsonObject generatedModel(
        Map<ResourceLocation, byte[]> assets, String path) {
        return com.google.gson.JsonParser
            .parseString(new String(assets.get(ResourceLocation.parse(path)),
                java.nio.charset.StandardCharsets.UTF_8))
            .getAsJsonObject();
    }

    @Test
    void faceTextureRulesAloneSubscribeVariablesAndGenerateOrderedOverrides()
        throws Exception {
        String original = crusher();
        write(original);
        String processor = MachineDefinitions.load(sources())
            .get(ResourceLocation.parse("test:crusher")).processor();
        int end = original.indexOf("/>", original.indexOf("<appearance"));
        String xml = original.substring(0, end) + ">\n"
            + "<face-texture id=\"blocked\" face=\"front\" texture=\"test:block/blocked\" when=\"$processors."
            + processor + ".status == 'blocked'\"/>\n"
            + "<face-texture id=\"running\" face=\"front\" texture=\"test:block/running\" emissive-mask=\"test:block/running_mask\" when=\"$processors."
            + processor + ".running\"/>\n"
            + "<face-texture id=\"full\" face=\"top\" texture=\"test:block/full\" when=\"$inventories.output.full\"/>\n"
            + "</appearance>" + original.substring(end + 2);
        write(xml);
        var definition = MachineDefinitions.load(sources())
            .get(ResourceLocation.parse("test:crusher"));
        assertTrue(definition.modelParts().isEmpty());
        assertTrue(definition.hasConditionalAppearance());
        assertEquals(3, definition.visualConditions().size());
        assertEquals(List.of("blocked", "running", "full"),
            definition.faceTextures().stream().map(rule -> rule.id()).toList());
        var assets = zone.moddev.mc.lib.machine.neoforge.MachineResourcePack
            .build(net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                Map.of(definition.id(), definition));
        var model = com.google.gson.JsonParser.parseString(new String(
            assets
                .get(ResourceLocation.parse("test:models/block/crusher.json")),
            java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("mmdlib:conditional_machine",
            model.get("loader").getAsString());
        var rules = model.getAsJsonArray("face_textures");
        assertEquals("front",
            rules.get(0).getAsJsonObject().get("face").getAsString());
        assertEquals("test:block/blocked",
            model.getAsJsonObject("textures")
                .get(
                    rules.get(0).getAsJsonObject().get("texture").getAsString())
                .getAsString());
        assertEquals("test:block/running",
            model.getAsJsonObject("textures")
                .get(
                    rules.get(1).getAsJsonObject().get("texture").getAsString())
                .getAsString());
        assertEquals(ResourceLocation.parse("test:block/running_mask"),
            definition.faceTextures().get(1).emissiveMask());
        assertEquals("test:block/running_mask",
            model.getAsJsonObject("textures").get(rules.get(1).getAsJsonObject()
                .get("emissive_mask").getAsString()).getAsString());
        for (String invalid : List.of(
            xml.replace("face=\"front\"", "face=\"north\""),
            xml.replace(".running", ".typo"),
            xml.replace("test:block/running", "Bad:texture"))) {
            write(invalid);
            assertThrows(IllegalArgumentException.class,
                () -> MachineDefinitions.load(sources()));
        }
    }

    @Test
    void conditionalPartsValidateComponentVariablesAndPreserveTheirSource()
        throws Exception {
        String original = crusher();
        int start = original.indexOf("<appearance");
        int end = original.indexOf("/>", start);
        String xml = original.substring(0, end) + ">\n"
            + "<part id=\"indicator\" model=\"test:block/light\" when=\"$processors.crushing.running\"/>\n"
            + "</appearance>" + original.substring(end + 2);
        // Use the fixture's actual processor ID rather than assuming a built-in
        // name.
        write(original);
        String processor = MachineDefinitions.load(sources())
            .get(ResourceLocation.parse("test:crusher")).processor();
        xml = xml.replace("processors.crushing", "processors." + processor);
        write(xml);
        var definition = MachineDefinitions.load(sources())
            .get(ResourceLocation.parse("test:crusher"));
        assertEquals(1, definition.modelParts().size());
        assertEquals(ResourceLocation.parse("test:block/light"),
            definition.modelParts().getFirst().model());
        assertEquals(false, definition.visualDefaults()
            .get("processors." + processor + ".running"));
        var assets = zone.moddev.mc.lib.machine.neoforge.MachineResourcePack
            .build(net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                Map.of(definition.id(), definition));
        var model = com.google.gson.JsonParser.parseString(new String(
            assets
                .get(ResourceLocation.parse("test:models/block/crusher.json")),
            java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("mmdlib:conditional_machine",
            model.get("loader").getAsString());
        assertEquals("test:block/crusher_base",
            model.getAsJsonObject("base").get("parent").getAsString());
        assertEquals("test:block/light",
            model.getAsJsonArray("parts").get(0).getAsJsonObject()
                .getAsJsonObject("model").get("parent").getAsString());
        assertTrue(assets.containsKey(
            ResourceLocation.parse("test:models/block/crusher_base.json")));
        for (String invalid : List.of(xml.replace(".running", ".typo"),
            xml.replace(".running", ".progress"),
            xml.replace("id=\"indicator\"", "id=\"base\""))) {
            write(invalid);
            var error = assertThrows(IllegalArgumentException.class,
                () -> MachineDefinitions.load(sources()));
            assertTrue(error.getMessage().contains("crusher.xml"));
        }
        write(original);
        var ordinary = MachineDefinitions.load(sources())
            .get(ResourceLocation.parse("test:crusher"));
        var unchanged = zone.moddev.mc.lib.machine.neoforge.MachineResourcePack
            .build(net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                Map.of(ordinary.id(), ordinary));
        assertFalse(unchanged.containsKey(
            ResourceLocation.parse("test:models/block/crusher_base.json")));
    }

    @Test
    void lavaFurnaceInheritsGenericBaseAndExpandsTanksWithoutPower()
        throws Exception {
        var xml = Files.readString(Path.of(
            "src/companion/resources/mmdlib/machine/simple_lava_furnace.xml"));
        write(
            xml.replace("<tank id=\"fuel\"", "<tanks count=\"2\" id=\"fuel\""));
        var plan = MachineDefinitions.load(sources())
            .get(ResourceLocation.parse("test:crusher"));
        assertNull(plan.power());
        assertEquals(List.of("fuel/0", "fuel/1"),
            plan.fluids().tanks().stream().map(t -> t.id()).toList());
        assertEquals(List.of(0, 1),
            plan.fluids().groups().getFirst().members());
        assertEquals(8000, plan.fluids().tanks().getFirst().capacity());
        assertEquals(ResourceLocation.parse("minecraft:lava"),
            plan.fluids().tanks().getFirst().fluid());
        for (var invalid : List.of(
            xml.replace("capacity=\"8000\"", "capacity=\"0\""),
            xml.replace("fluid=\"minecraft:lava\"", "fluid=\"Invalid:lava\""),
            xml.replace("<fluids id=\"fluids\"", "<fluids id=\"items\""),
            xml.replace("<tank id=\"fuel\"", "<tank id=\"input\""),
            xml.replace("input fuel", "input"))) {
            write(invalid);
            assertThrows(IllegalArgumentException.class,
                () -> MachineDefinitions.load(sources()), invalid);
        }
    }

    @Test
    void manualTanksCanRemainRecipeGroupsWithoutAutomation() throws Exception {
        var xml = crusher()
            .replace("filter-inputs=\"input\"",
                "filter-inputs=\"input reagent\"")
            .replace("<storage>",
                """
                    <storage>
                      <fluids id="fluids">
                        <tank id="water" capacity="1000" role="manual" fluid="minecraft:water" automation="false"/>
                        <tanks id="reagent" count="2" capacity="1000" role="input"/>
                      </fluids>
                    """);
        write(xml);
        var machine = MachineDefinitions.load(sources())
            .get(ResourceLocation.parse("test:crusher"));
        assertEquals(3, machine.fluids().tanks().size());
        assertFalse(machine.fluids().groups().getFirst().automation());
        assertEquals(zone.moddev.mc.lib.api.capabilities.IoMode.DISABLED,
            machine.fluids().groups().getFirst().mode());
        assertTrue(machine.fluids().groups().getLast().automation());
        write(xml.replace("<processor",
            """
                <groups><group id="water" storage="fluids" mode="disabled" color="#123456"/></groups><processor
                """));
        machine = MachineDefinitions.load(sources()).get(machine.id());
        assertFalse(machine.fluids().groups().getFirst().automation());
        assertEquals(0x123456, machine.fluids().groups().getFirst().color());
        write(xml.replace("role=\"manual\"", "role=\"input\""));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> MachineDefinitions.load(sources())).getMessage()
            .contains("role=manual"));
        write(xml.replace("automation=\"false\"", "automation=\"invalid\""));
        assertThrows(IllegalArgumentException.class,
            () -> MachineDefinitions.load(sources()));
    }

    @Test
    void inheritsPowerAndExpandsStableGroupsAndCustomProperties()
        throws Exception {
        write(crusher().replace("<items id=\"items\">",
            "<energy id=\"energy\" capacity=\"200000\"/><items id=\"items\">"));
        var machine = MachineDefinitions.load(sources())
            .get(ResourceLocation.parse("test:crusher"));
        assertEquals(200000, machine.power().capacity());
        assertEquals(1000, machine.power().input());
        assertEquals(0xd85b5b, machine.power().color());
        assertEquals(List.of("input/0", "output/0", "output/1"),
            machine.slots().stream().map(s -> s.id()).toList());
        assertEquals(List.of(1, 2), machine.groups().get(1).members());
        assertEquals(Map.of("hardness-level-ratio", ".10"),
            machine.recipeType().generator().properties());
        assertFalse(
            machine.recipeType().generator().properties().containsKey("class"));
        assertEquals("mmdlib:base_powered_machine",
            machine.screen().attributes().get("extends"));
    }

    @Test
    void growingCountKeepsExistingMemberIdentities() throws Exception {
        write(crusher().replace("count=\"2\" role=\"output\"",
            "count=\"3\" role=\"output\""));
        var machine = MachineDefinitions.load(sources()).values().iterator()
            .next();
        assertEquals(List.of("input/0", "output/0", "output/1", "output/2"),
            machine.slots().stream().map(s -> s.id()).toList());
    }

    @Test
    void honorsGroupColorAndProcessorNameAndManualSlots() throws Exception {
        write(crusher().replace("<processor",
            "<groups><group id=\"energy\" storage=\"energy\""
                + " color=\"#123456\"/></groups><processor name=\"Crushing\"")
            .replace("</items>",
                "<slots id=\"upgrades\" count=\"1\" role=\"manual\"/></items>"));
        var machine = MachineDefinitions.load(sources()).values().iterator()
            .next();
        assertEquals(0x123456, machine.power().color());
        assertEquals("Crushing", machine.processorName());
        assertEquals(zone.moddev.mc.lib.api.capabilities.IoMode.DISABLED,
            machine.groups().getLast().mode());
    }

    @Test
    void readsProcessorSounds() throws Exception {
        var id = ResourceLocation.parse("test:crusher");
        write(crusher());
        assertEquals(java.util.Optional.empty(),
            MachineDefinitions.load(sources()).get(id).sounds().running());
        write(crusher().replace("<processor",
            "<processor running-sound=\"test:machine/hum\""
                + " finish-sound=\"test:machine/ding\""));
        var sounds = MachineDefinitions.load(sources()).get(id).sounds();
        assertEquals(ResourceLocation.parse("test:machine/hum"),
            sounds.running().orElseThrow());
        assertEquals(ResourceLocation.parse("test:machine/ding"),
            sounds.finish().orElseThrow());
        write(crusher().replace("<processor",
            "<processor on-start=\"test:machine/started\""
                + " on-finish=\"test:machine/finished\""));
        var events = MachineDefinitions.load(sources()).get(id).events();
        assertEquals(ResourceLocation.parse("test:machine/started"),
            events.start().orElseThrow());
        assertEquals(ResourceLocation.parse("test:machine/finished"),
            events.finish().orElseThrow());
        write(crusher().replace("<processor",
            "<processor on-finish=\"Not A Handler\""));
        var badEvent = assertThrows(IllegalArgumentException.class,
            () -> MachineDefinitions.load(sources()));
        assertTrue(badEvent.getMessage().contains("on-finish"),
            badEvent.getMessage());
        write(crusher().replace("<processor",
            "<processor finish-sound=\"Not A Sound\""));
        var bad = assertThrows(IllegalArgumentException.class,
            () -> MachineDefinitions.load(sources()));
        assertTrue(bad.getMessage().contains("finish-sound"), bad.getMessage());
    }

    @Test
    void rejectsUnknownAndOverlappingAndUnsafeDefinitions() throws Exception {
        write(crusher().replace("capacity=", "capcity=").replace("<storage>",
            "<storage bogus=\"true\">"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> MachineDefinitions.load(sources())).getMessage()
            .contains("bogus"));
        write(crusher().replace("<processor",
            "<groups><group id=\"duplicate\" storage=\"items\" members=\"input/0\""
                + " mode=\"input\"/></groups><processor"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> MachineDefinitions.load(sources())).getMessage()
            .contains("Overlapping"));
        write("<!DOCTYPE machine [<!ENTITY attack SYSTEM 'file:///missing'>]>"
            + crusher());
        assertThrows(IllegalArgumentException.class,
            () -> MachineDefinitions.load(sources()));
    }

    @Test
    void rejectsReservedStorageKeysAtDefinitionLoad() throws Exception {
        for (var key : List.of("energy", "automation", "features",
            "redstone_control", "redstone_mode", "id", "components",
            "mmdlib_features", "mmdlib_machine")) {
            write(crusher().replace("<items id=\"items\">",
                "<items id=\"" + key + "\">"));
            assertThrows(IllegalArgumentException.class,
                () -> MachineDefinitions.load(sources()), key);
        }
    }

    @Test
    void checksumIncludesInheritedSettingsButNotPathsOrFormatting()
        throws Exception {
        var child = crusher().replace("mmdlib:base_powered_machine",
            "test:base");
        write(child);
        var base = Files.readString(Path
            .of("src/main/resources/mmdlib/machine/base_powered_machine.xml"));
        Files.writeString(directory.resolve("machine/base.xml"), base);
        var id = ResourceLocation.parse("test:crusher");
        var original = MachineDefinitions.load(sources()).get(id).checksum();
        write(child
            .replace("count=\"1\" role=\"input\"", "role=\"input\" count=\"1\"")
            .replace("<storage>", "<!-- spacing -->\n  <storage>"));
        assertEquals(original,
            MachineDefinitions.load(sources()).get(id).checksum());
        var copied = directory.resolve("copy");
        Files.createDirectories(copied.resolve("machine"));
        Files.writeString(copied.resolve("machine/base.xml"), base);
        Files.writeString(copied.resolve("machine/crusher.xml"), child);
        assertEquals(original,
            MachineDefinitions.load(List.of(sources().getFirst(),
                new SourceRoot("test", copied, getClass().getClassLoader())))
                .get(id).checksum());
        write(child);
        Files.writeString(directory.resolve("machine/base.xml"),
            base.replace("capacity=\"100000\"", "capacity=\"200000\""));
        assertNotEquals(original,
            MachineDefinitions.load(sources()).get(id).checksum());
        Files.writeString(directory.resolve("machine/base.xml"), base);
        for (var edited : List.of(
            child.replace("role=\"input\"", "role=\"input\" limit=\"16\""),
            child.replace("role=\"output\"", "role=\"storage\""),
            child.replace("id=\"crusher\"", "id=\"other\""),
            child.replace("hardness-level-ratio=\".10\"",
                "hardness-level-ratio=\".20\""))) {
            write(edited);
            assertNotEquals(original,
                MachineDefinitions.load(sources()).get(id).checksum());
        }
    }

    @Test
    void singletonOverridesCanChangeTheirIds() throws Exception {
        write(
            """
                <machine xmlns="https://moddev.zone/mmdlib/machine" version="1" extends="test:base" name="Child">
                  <storage><items id="renamed_items"/></storage>
                  <recipe-type id="test:other"/>
                  <processor id="other_processor" recipe-type="test:other"/>
                </machine>
                """);
        Files.writeString(directory.resolve("machine/base.xml"),
            crusher().replace("<machine ", "<machine abstract=\"true\" "));
        var plan = MachineDefinitions.load(sources())
            .get(ResourceLocation.parse("test:crusher"));
        assertEquals("renamed_items", plan.itemStorageId());
        assertEquals("other_processor", plan.processor());
        assertEquals(ResourceLocation.parse("test:other"),
            plan.recipeType().id());
        assertEquals(3, plan.slots().size());
        assertNotNull(plan.recipeType().generator());
        assertEquals(100000, plan.power().capacity());
        write(crusher().replace("</machine>",
            "<processor id=\"duplicate\" recipe-type=\"test:other\"/></machine>"));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> MachineDefinitions.load(sources())).getMessage()
            .contains("Duplicate definition processor"));
    }

    @Test
    void inheritedScreensPreserveEveryUnnamedSiblingInOrder() throws Exception {
        write(crusher().replace("mmdlib:base_powered_machine", "test:base")
            .replace("</machine>",
                "<screen><hbox><label text=\"third\"/><label text=\"fourth\"/></hbox><hbox><label"
                    + " text=\"fifth\"/></hbox></screen></machine>"));
        var base = Files
            .readString(Path.of(
                "src/main/resources/mmdlib/machine/base_powered_machine.xml"))
            .replace("extends=\"mmdlib:base_powered_machine\"/>",
                "><hbox><label text=\"first\"/><label text=\"second\"/></hbox><hbox><label"
                    + " text=\"base_end\"/></hbox></screen>");
        Files.writeString(directory.resolve("machine/base.xml"), base);
        var plan = MachineDefinitions.load(sources())
            .get(ResourceLocation.parse("test:crusher"));
        var children = plan.screen().children().stream()
            .filter(n -> n.name().equals("hbox")).toList();
        assertEquals(4, children.size());
        assertEquals(
            List.of("first", "second", "base_end", "third", "fourth", "fifth"),
            children.stream().flatMap(n -> n.children().stream())
                .map(n -> n.attributes().get("text")).toList());
        var standaloneAttributes = new HashMap<>(plan.screen().attributes());
        standaloneAttributes.remove("extends");
        var built = zone.moddev.mc.lib.gui.load.ScreenLoader.read(
            Map.of(plan.id(),
                new zone.moddev.mc.lib.gui.load.ScreenReader.Node("screen",
                    standaloneAttributes, children, plan.screen().source(),
                    plan.screen().line())),
            new zone.moddev.mc.lib.gui.WidgetTypes(),
            zone.moddev.mc.lib.gui.load.ScreenFillGenerator.UNAVAILABLE);
        assertEquals(6, built.get(plan.id()).elements().stream().filter(
            e -> e instanceof zone.moddev.mc.lib.gui.model.elements.LabelElement)
            .count());
    }

    @Test
    void malformedValuesNameTheirSourceAttributeAndValue() throws Exception {
        var edits = new LinkedHashMap<String, String>();
        edits.put("extends='Invalid:base'",
            crusher().replace("mmdlib:base_powered_machine", "Invalid:base"));
        edits.put("front='Invalid:front'", crusher()
            .replace("minecraft:block/furnace_front", "Invalid:front"));
        edits.put("bottom='Invalid:bottom'",
            crusher().replace("preset=\"cube-front\"",
                "preset=\"cube-front\" bottom=\"Invalid:bottom\""));
        edits.put("mineable='Invalid:tool'", crusher().replace("<storage>",
            "<block mineable=\"Invalid:tool\"/><storage>"));
        edits.put("id='Invalid:type'",
            crusher().replace(
                "<recipe-type id=\"mmdlib_companion:simple_crushing\"",
                "<recipe-type id=\"Invalid:type\""));
        edits.put("recipe-type='Invalid:type'",
            crusher().replace(
                "recipe-type=\"mmdlib_companion:simple_crushing\"",
                "recipe-type=\"Invalid:type\""));
        for (var value : List.of("3,5", "broken", "NaN", "Infinity", "-1"))
            edits.put("hardness='" + value + "'", crusher().replace("<storage>",
                "<block hardness=\"" + value + "\"/><storage>"));
        for (var edit : edits.entrySet()) {
            write(edit.getValue());
            var message = assertThrows(
                zone.moddev.mc.lib.gui.load.ScreenException.class,
                () -> MachineDefinitions.load(sources())).getMessage();
            assertTrue(message.matches("(?s).*crusher\\.xml:[1-9][0-9]*:.*"),
                message);
            assertTrue(message.contains(edit.getKey()), message);
            if (edit.getKey().contains("3,5"))
                assertTrue(message.contains("3.5"), message);
        }
        write(crusher().replace("mmdlib:base_powered_machine", "test:base")
            .replace("<storage>", "<block hardness=\"3.5\"/><storage>"));
        Files.writeString(directory.resolve("machine/base.xml"), Files
            .readString(
                Path.of("src/main/resources/mmdlib/machine/base_machine.xml"))
            .replace("hardness=\"3.5\"", "hardness=\"3,5\""));
        var inheritedError = assertThrows(
            zone.moddev.mc.lib.gui.load.ScreenException.class,
            () -> MachineDefinitions.load(sources())).getMessage();
        assertTrue(
            inheritedError
                .matches("(?s).*base\\.xml:[1-9][0-9]*:.*hardness='3,5'.*"),
            inheritedError);
    }

    @Test
    void rejectsCyclesBeforeProducingPlans() throws Exception {
        write(
            "<machine xmlns=\"https://moddev.zone/mmdlib/machine\" version=\"1\""
                + " extends=\"test:crusher\"/>");
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> MachineDefinitions.load(sources())).getMessage()
            .contains("cycle"));
    }

    @Test
    void typedPropertiesReportSourceAndRejectTyposAndRanges() {
        var p = new GeneratorProperties(Map.of("energy", "-1", "tiem", "100"),
            "crusher.xml", 17);
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> p.requiredLong("energy", 1, Long.MAX_VALUE)).getMessage()
            .contains("crusher.xml:17"));
        assertThrows(IllegalArgumentException.class,
            () -> p.requireOnly("energy", "time"));
        assertThrows(IllegalArgumentException.class,
            () -> p.requiredString("missing"));
    }
}
