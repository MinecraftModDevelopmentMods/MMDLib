package zone.moddev.mc.lib.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.load.ScreenLoader;
import zone.moddev.mc.lib.gui.model.containers.ColouredGroupElement;
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.definition.MachineDefinitions;
import zone.moddev.mc.lib.machine.definition.MachineScreenGenerators;

class MachineInlineColorsTest {
    @TempDir
    Path directory;

    private List<SourceRoot> sources() {
        return List.of(
            new SourceRoot("mmdlib", Path.of("src/main/resources/mmdlib"),
                getClass().getClassLoader()),
            new SourceRoot("test", directory, getClass().getClassLoader()));
    }

    private String factory() {
        return """
            <machine xmlns="https://moddev.zone/mmdlib/machine" version="1"
                     extends="mmdlib:base_powered_machine" name="Factory">
              <appearance preset="cube-front" front="minecraft:block/furnace_front"
                          side="minecraft:block/iron_block" top="minecraft:block/smooth_stone"/>
              <storage>
                <energy capacity="32000" input-rate="128" color="#123aBc"/>
                <items id="items">
                  <slots id="input" count="1" role="input" color="input.secondary"/>
                  <slots id="output" count="4" role="output" color="output.primary"/>
                </items>
                <fluids id="fluids">
                  <tank id="water" capacity="1000" role="input" fluid="minecraft:water" color="fluid.primary"/>
                  <tanks id="lava" count="2" capacity="1000" role="input" fluid="minecraft:lava" color="fluid.secondary"/>
                </fluids>
              </storage>
              <recipe-type id="test:production" declare="true" name="Production"/>
              <processor id="factory" recipe-type="test:production"/>
            </machine>
            """;
    }

    private MachineDefinition load(String xml) throws Exception {
        Files.createDirectories(directory.resolve("machine"));
        Files.writeString(directory.resolve("machine/factory.xml"), xml);
        return MachineDefinitions.load(sources()).get(ResourceLocation.parse("test:factory"));
    }

    @Test
    void inlineNamesAndHexReachGroupsAndGeneratedOutlines() throws Exception {
        var machine = load(factory());
        assertEquals(0x123abc, machine.power().color());
        assertEquals(128, machine.power().input());
        assertEquals(0, machine.power().output());
        assertEquals(List.of(0xa184ce, 0x4dc6d8),
            machine.groups().stream().map(MachineDefinition.Group::color).toList());
        assertEquals(List.of(0x508ee0, 0xee963c),
            machine.fluids().groups().stream().map(MachineDefinition.Group::color).toList());
        assertEquals(List.of(1, 2), machine.fluids().groups().get(1).members());
        var screen = ScreenLoader.load(sources(), Map.of(machine.id(), machine.screen()),
            (id, node) -> MachineScreenGenerators.generate(machine, node)).get(machine.id())
            .screen();
        assertEquals(0xff4dc6d8,
            ((ColouredGroupElement) screen.find("inventory_output").orElseThrow()).colour());
        assertEquals(List.of("inventory_output"),
            MachineScreenGenerators.compileHighlights(machine, screen).get("output"));
    }

    @Test
    void explicitGroupsOverrideInlineColorsWithoutChangingMembers() throws Exception {
        var inline = load(factory());
        var explicit = load(factory().replace("</machine>", """
              <groups>
                <group id="energy" storage="energy" mode="input" color="energy"/>
                <group id="output" storage="items" mode="output" color="#AbCDef"/>
                <group id="water" storage="fluids" mode="input" color="fluid.tertiary"/>
              </groups>
            </machine>
            """));
        assertEquals(0xd85b5b, explicit.power().color());
        assertEquals(0xabcdef, explicit.groups().get(1).color());
        assertEquals(0x9c83cf, explicit.fluids().groups().getFirst().color());
        assertEquals(inline.slots(), explicit.slots());
        assertEquals(inline.fluids().tanks(), explicit.fluids().tanks());
        assertEquals(inline.groups().get(1).members(), explicit.groups().get(1).members());
    }

    @Test
    void omittedGroupColorsPreserveInlineChoices() throws Exception {
        var inline = load(factory());
        var explicit = load(factory().replace("</machine>", """
              <groups>
                <group id="output" storage="items" mode="output"/>
                <group id="water" storage="fluids" mode="input"/>
              </groups>
            </machine>
            """));
        assertEquals(inline.groups(), explicit.groups());
        assertEquals(inline.fluids(), explicit.fluids());
    }

    @Test
    void defaultsAdvancePerGroupNotPerSlotAndKeepExplicitGroupsInOrder() throws Exception {
        var xml = factory().replaceAll(" color=\"[^\"]*\"", "")
            .replace("</items>", """
                  <slots id="extra_input" count="2" role="input" color="#010203"/>
                  <slots id="third_input" count="1" role="input"/>
                  <slots id="fourth_input" count="1" role="input"/>
                  <slots id="extra_output" count="1" role="output"/>
                  <slots id="buffer" count="1" role="storage"/>
                  <slots id="manual" count="1" role="manual"/>
                </items>
                  """);
        var plan = load(xml);
        assertEquals(
            List.of(0x568bd8, 0x4dc6d8, 0x010203, 0xcf84aa, 0x568bd8, 0x83b967, 0x8c9eac, 0xaaa0b7),
            plan.groups().stream().map(MachineDefinition.Group::color).toList());
        assertEquals(List.of(0x508ee0, 0xee963c),
            plan.fluids().groups().stream().map(MachineDefinition.Group::color).toList());
        assertEquals(0xd85b5b, plan.power().color());
    }

    @Test
    void inheritancePreservesAndOverridesInlineColorsAndFingerprints() throws Exception {
        load(factory());
        Files.writeString(directory.resolve("machine/base.xml"),
            factory().replace("version=\"1\"", "version=\"1\" abstract=\"true\""));
        var child = """
            <machine xmlns="https://moddev.zone/mmdlib/machine" version="1" extends="test:base" name="Child">
              <storage><energy capacity="64000"/><items id="items"><slots id="output" color="output.tertiary"/></items></storage>
            </machine>
            """;
        var plan = load(child);
        assertEquals(0x123abc, plan.power().color());
        assertEquals(0xd9bb62, plan.groups().get(1).color());
        assertEquals(0xa184ce, plan.groups().getFirst().color());
        assertEquals(4, plan.groups().get(1).members().size());
        assertNotEquals(plan.checksum(),
            load(child.replace("output.tertiary", "output.primary")).checksum());
    }

    @Test
    void invalidColorsFailAtTheirOriginalSourceEvenWhenOverridden() throws Exception {
        for (var value : List.of("input_99", "#12345", "#gg0000", "#12345678", "")) {
            var error = assertThrows(ScreenException.class,
                () -> load(factory().replace("input.secondary", value)));
            assertTrue(error.getMessage().contains("factory.xml:"), error.getMessage());
            assertTrue(error.getMessage().contains("color='" + value + "'"), error.getMessage());
            assertTrue(error.getMessage().contains("input.primary"), error.getMessage());
        }
        load(factory());
        Files.writeString(directory.resolve("machine/base.xml"), factory()
            .replace("version=\"1\"", "version=\"1\" abstract=\"true\"")
            .replace("input.secondary", "typo"));
        var error = assertThrows(ScreenException.class, () -> load(
            """
                <machine xmlns="https://moddev.zone/mmdlib/machine" version="1" extends="test:base" name="Child">
                  <storage><items id="items"><slots id="input" color="input.primary"/></items></storage>
                </machine>
                """));
        assertTrue(error.getMessage().contains("base.xml:"), error.getMessage());
        assertTrue(error.getMessage().contains("color='typo'"), error.getMessage());
    }
}
