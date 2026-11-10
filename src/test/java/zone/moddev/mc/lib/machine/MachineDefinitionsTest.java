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

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
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
