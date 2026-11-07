package zone.moddev.mc.lib.machine;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import zone.moddev.mc.lib.machine.definition.MachineDefinitions;
import zone.moddev.mc.lib.machine.recipe.generation.GeneratorProperties;

class MachineDefinitionsTest {
  @TempDir Path directory;

  private List<SourceRoot> sources() {
    return List.of(
        new SourceRoot("mmdlib", Path.of("src/main/resources/mmdlib"), getClass().getClassLoader()),
        new SourceRoot("test", directory, getClass().getClassLoader()));
  }

  private void write(String text) throws Exception {
    Files.createDirectories(directory.resolve("machine"));
    Files.writeString(directory.resolve("machine/crusher.xml"), text);
  }

  private String crusher() throws Exception {
    return Files.readString(Path.of("src/companion/resources/mmdlib/machine/simple_crusher.xml"));
  }

  @Test
  void inheritsPowerAndExpandsStableGroupsAndCustomProperties() throws Exception {
    write(
        crusher()
            .replace(
                "<items id=\"items\">",
                "<energy id=\"energy\" capacity=\"200000\"/><items id=\"items\">"));
    var machine = MachineDefinitions.load(sources()).get(ResourceLocation.parse("test:crusher"));
    assertEquals(200000, machine.power().capacity());
    assertEquals(1000, machine.power().input());
    assertEquals(0xd85b5b, machine.power().color());
    assertEquals(
        List.of("input/0", "output/0", "output/1"),
        machine.slots().stream().map(s -> s.id()).toList());
    assertEquals(List.of(1, 2), machine.groups().get(1).members());
    assertEquals(
        Map.of("hardness-level-ratio", ".10"), machine.recipeType().generator().properties());
    assertFalse(machine.recipeType().generator().properties().containsKey("class"));
    assertEquals("mmdlib:base_powered_machine", machine.screen().attributes().get("extends"));
  }

  @Test
  void growingCountKeepsExistingMemberIdentities() throws Exception {
    write(crusher().replace("count=\"2\" role=\"output\"", "count=\"3\" role=\"output\""));
    var machine = MachineDefinitions.load(sources()).values().iterator().next();
    assertEquals(
        List.of("input/0", "output/0", "output/1", "output/2"),
        machine.slots().stream().map(s -> s.id()).toList());
  }

  @Test
  void honorsGroupColorAndProcessorNameAndManualSlots() throws Exception {
    write(
        crusher()
            .replace(
                "<processor",
                "<groups><group id=\"energy\" storage=\"energy\""
                    + " color=\"#123456\"/></groups><processor name=\"Crushing\"")
            .replace("</items>", "<slots id=\"upgrades\" count=\"1\" role=\"manual\"/></items>"));
    var machine = MachineDefinitions.load(sources()).values().iterator().next();
    assertEquals(0x123456, machine.power().color());
    assertEquals("Crushing", machine.processorName());
    assertEquals(
        zone.moddev.mc.lib.api.capabilities.IoMode.DISABLED, machine.groups().getLast().mode());
  }

  @Test
  void rejectsUnknownAndOverlappingAndUnsafeDefinitions() throws Exception {
    write(
        crusher()
            .replace("capacity=", "capcity=")
            .replace("<storage>", "<storage bogus=\"true\">"));
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> MachineDefinitions.load(sources()))
            .getMessage()
            .contains("bogus"));
    write(
        crusher()
            .replace(
                "<processor",
                "<groups><group id=\"duplicate\" storage=\"items\" members=\"input/0\""
                    + " mode=\"input\"/></groups><processor"));
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> MachineDefinitions.load(sources()))
            .getMessage()
            .contains("Overlapping"));
    write("<!DOCTYPE machine [<!ENTITY attack SYSTEM 'file:///missing'>]>" + crusher());
    assertThrows(IllegalArgumentException.class, () -> MachineDefinitions.load(sources()));
  }

  @Test
  void rejectsReservedStorageKeysAtDefinitionLoad() throws Exception {
    for (var key :
        List.of(
            "energy",
            "automation",
            "features",
            "redstone_control",
            "redstone_mode",
            "id",
            "components",
            "mmdlib_features",
            "mmdlib_machine")) {
      write(crusher().replace("<items id=\"items\">", "<items id=\"" + key + "\">"));
      assertThrows(IllegalArgumentException.class, () -> MachineDefinitions.load(sources()), key);
    }
  }

  @Test
  void checksumIncludesInheritedSettingsButNotPathsOrFormatting() throws Exception {
    var child = crusher().replace("mmdlib:base_powered_machine", "test:base");
    write(child);
    var base =
        Files.readString(Path.of("src/main/resources/mmdlib/machine/base_powered_machine.xml"));
    Files.writeString(directory.resolve("machine/base.xml"), base);
    var id = ResourceLocation.parse("test:crusher");
    var original = MachineDefinitions.load(sources()).get(id).checksum();
    write(
        child
            .replace("count=\"1\" role=\"input\"", "role=\"input\" count=\"1\"")
            .replace("<storage>", "<!-- spacing -->\n  <storage>"));
    assertEquals(original, MachineDefinitions.load(sources()).get(id).checksum());
    var copied = directory.resolve("copy");
    Files.createDirectories(copied.resolve("machine"));
    Files.writeString(copied.resolve("machine/base.xml"), base);
    Files.writeString(copied.resolve("machine/crusher.xml"), child);
    assertEquals(
        original,
        MachineDefinitions.load(
                List.of(new SourceRoot("test", copied, getClass().getClassLoader())))
            .get(id)
            .checksum());
    write(child);
    Files.writeString(
        directory.resolve("machine/base.xml"),
        base.replace("capacity=\"100000\"", "capacity=\"200000\""));
    assertNotEquals(original, MachineDefinitions.load(sources()).get(id).checksum());
    Files.writeString(directory.resolve("machine/base.xml"), base);
    for (var edited :
        List.of(
            child.replace("role=\"input\"", "role=\"input\" limit=\"16\""),
            child.replace("role=\"output\"", "role=\"storage\""),
            child.replace("id=\"crusher\"", "id=\"other\""),
            child.replace("hardness-level-ratio=\".10\"", "hardness-level-ratio=\".20\""))) {
      write(edited);
      assertNotEquals(original, MachineDefinitions.load(sources()).get(id).checksum());
    }
  }

  @Test
  void rejectsCyclesBeforeProducingPlans() throws Exception {
    write(
        "<machine xmlns=\"https://moddev.zone/mmdlib/machine\" version=\"1\""
            + " extends=\"test:crusher\"/>");
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> MachineDefinitions.load(sources()))
            .getMessage()
            .contains("cycle"));
  }

  @Test
  void typedPropertiesReportSourceAndRejectTyposAndRanges() {
    var p = new GeneratorProperties(Map.of("energy", "-1", "tiem", "100"), "crusher.xml", 17);
    assertTrue(
        assertThrows(
                IllegalArgumentException.class, () -> p.requiredLong("energy", 1, Long.MAX_VALUE))
            .getMessage()
            .contains("crusher.xml:17"));
    assertThrows(IllegalArgumentException.class, () -> p.requireOnly("energy", "time"));
    assertThrows(IllegalArgumentException.class, () -> p.requiredString("missing"));
  }
}
