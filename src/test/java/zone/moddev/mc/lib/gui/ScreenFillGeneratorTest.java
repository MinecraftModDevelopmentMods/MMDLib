package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.*;
import zone.moddev.mc.lib.gui.load.ScreenReader.Node;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.machine.definition.*;

class ScreenFillGeneratorTest {
  @TempDir Path directory;

  private void write(String name, String attrs, String body) throws Exception {
    Files.createDirectories(directory.resolve("screen"));
    Files.writeString(
        directory.resolve("screen/" + name + ".xml"),
        "<screen xmlns=\"https://moddev.zone/mmdlib/screen\" " + attrs + ">" + body + "</screen>");
  }

  @Test
  void defaultsOnlyRunWhenNoFillOverridesThemIncludingEmptyFills() throws Exception {
    write(
        "base",
        "abstract=\"true\" block-entity=\"zone.moddev.mc.lib.gui.ScreenLoaderTest$Press\"",
        "<vbox><label text=\"test.header\"/><placeholder name=\"body\""
            + " generator=\"test:default\"/></vbox>");
    write("default", "extends=\"test:base\" title=\"test.default\"", "");
    write(
        "manual",
        "extends=\"test:base\" title=\"test.manual\"",
        "<fill name=\"body\"><slot id=\"manual\" index=\"2\"/></fill>");
    write("empty", "extends=\"test:base\" title=\"test.empty\"", "<fill name=\"body\"/>");
    write(
        "middle",
        "abstract=\"true\" extends=\"test:base\"",
        "<fill name=\"body\" generator=\"test:replacement\" columns=\"2\"/>");
    write("leaf", "extends=\"test:middle\" title=\"test.leaf\"", "");
    var calls = new ArrayList<String>();
    var screens =
        ScreenLoader.load(
            List.of(new SourceRoot("test", directory, getClass().getClassLoader())),
            Map.of(),
            (id, node) -> {
              calls.add(id + "=" + node.attributes().get("generator"));
              return List.of(
                  new Node(
                      "slot",
                      Map.of("id", "generated", "index", "0"),
                      List.of(),
                      node.source(),
                      node.line()));
            });
    assertEquals(List.of("test:default=test:default", "test:leaf=test:replacement"), calls);
    assertTrue(
        screens.get(ResourceLocation.parse("test:empty")).screen().find("generated").isEmpty());
    assertTrue(
        screens.get(ResourceLocation.parse("test:manual")).screen().find("manual").isPresent());
    assertInstanceOf(
        Element.Slot.class,
        screens
            .get(ResourceLocation.parse("test:default"))
            .screen()
            .find("generated")
            .orElseThrow());
  }

  @Test
  void realSimpleCrusherGeneratesSlotsAndInheritsCommonScreen() {
    var sources =
        List.of(
            new SourceRoot(
                "mmdlib", Path.of("src/main/resources/mmdlib"), getClass().getClassLoader()),
            new SourceRoot(
                "mmdlib_companion",
                Path.of("src/companion/resources/mmdlib"),
                getClass().getClassLoader()));
    var machines = MachineDefinitions.load(sources);
    var id = ResourceLocation.parse("mmdlib_companion:simple_crusher");
    // Only load library screens; the other companion screens bind companion-only classes.
    var screens =
        ScreenLoader.load(
            List.of(sources.getFirst()),
            Map.of(id, machines.get(id).screen()),
            (key, node) -> MachineScreenGenerators.generate(machines.get(key), node));
    var screen = screens.get(id).screen();
    assertDoesNotThrow(() -> MachineScreenGenerators.validate(machines.get(id), screen));
    var highlights = MachineScreenGenerators.compileHighlights(machines.get(id), screen);
    assertEquals(List.of("slot_0"), highlights.get("input"));
    assertEquals(List.of("slot_1", "slot_2"), highlights.get("output"));
    assertEquals(List.of("energy"), highlights.get("energy"));
    assertThrows(UnsupportedOperationException.class, () -> highlights.put("other", List.of()));
    assertThrows(UnsupportedOperationException.class, () -> highlights.get("input").add("other"));
    assertEquals(
        List.of(0, 1, 2),
        screen.elements().stream()
            .filter(e -> e instanceof Element.Slot)
            .map(e -> ((Element.Slot) e).index())
            .toList());
    assertTrue(screen.find("energy").isPresent());
    assertTrue(screen.find("info").isPresent());
    assertTrue(screen.find("generated_progress").isPresent());
    var progress = screen.find("generated_progress").orElseThrow();
    assertEquals(List.of(machines.get(id).recipeType().id()), progress.common().jeiRecipes());
    var layout = ScreenLayout.of(screen, TextMeasure.NONE);
    var input = layout.bounds(screen.find("slot_0").orElseThrow());
    var output = layout.bounds(screen.find("slot_2").orElseThrow());
    var energy = layout.bounds(screen.find("energy").orElseThrow());
    // Center the entire processing cluster in the space before the fixed energy gauge.
    assertEquals(7 + energy.x() - 6, input.x() + output.right(), 1);
    assertEquals(energy.y() * 2 + energy.height(), input.y() * 2 + input.height(), 1);
    assertEquals(input.y(), output.y());
    assertEquals(
        input.y() * 2 + input.height(),
        layout.bounds(progress).y() * 2 + layout.bounds(progress).height(),
        1);
    var processorNodes =
        MachineScreenGenerators.generate(
            machines.get(id),
            new Node(
                "fill",
                Map.of("name", "processor", "generator", "mmdlib:processors"),
                List.of(),
                "test.xml",
                1));
    assertEquals(
        machines.get(id).recipeType().id().toString(),
        processorNodes.getFirst().attributes().get("jei-recipes"));
    var invalid =
        ScreenLoader.load(
            List.of(sources.getFirst()),
            Map.of(id, machines.get(id).screen()),
            (key, node) ->
                List.of(
                    new Node(
                        "slot", Map.of("index", "99"), List.of(), node.source(), node.line())));
    assertThrows(
        IllegalArgumentException.class,
        () -> MachineScreenGenerators.validate(machines.get(id), invalid.get(id).screen()));
  }

  @Test
  void literalTemplateAttributesRejectTyposBeforeExpansion() throws Exception {
    var sources = List.of(new SourceRoot("test", directory, getClass().getClassLoader()));
    for (var attribute : List.of("colums=\"2\"", "generatr=\"test:fill\"")) {
      write(
          "base",
          "abstract=\"true\" block-entity=\"zone.moddev.mc.lib.gui.ScreenLoaderTest$Press\"",
          "<placeholder name=\"body\" " + attribute + "/>");
      write("child", "extends=\"test:base\" title=\"test.child\"", "<fill name=\"body\"/>");
      assertTrue(
          assertThrows(ScreenException.class, () -> ScreenLoader.load(sources))
              .getMessage()
              .contains("Unknown attribute"));
      write(
          "base",
          "abstract=\"true\" block-entity=\"zone.moddev.mc.lib.gui.ScreenLoaderTest$Press\"",
          "<placeholder name=\"body\"/>");
      write(
          "child",
          "extends=\"test:base\" title=\"test.child\"",
          "<fill name=\"body\" " + attribute + "/>");
      assertTrue(
          assertThrows(ScreenException.class, () -> ScreenLoader.load(sources))
              .getMessage()
              .contains("Unknown attribute"));
    }
    var invalid =
        new Node(
            "screen",
            Map.of("title", "test", "block-entity", "test.Host"),
            List.of(
                new Node(
                    "placeholder",
                    Map.of("name", "body", "colums", "2"),
                    List.of(),
                    "machine.xml",
                    7)),
            "machine.xml",
            1);
    var error =
        assertThrows(
            ScreenException.class,
            () ->
                ScreenLoader.read(
                    Map.of(ResourceLocation.parse("test:embedded"), invalid),
                    new WidgetTypes(),
                    ScreenFillGenerator.UNAVAILABLE));
    assertTrue(error.getMessage().contains("machine.xml:7: Unknown attribute colums"));
  }

  @Test
  void rejectsGeneratorWithLiteralChildren() throws Exception {
    write(
        "base",
        "abstract=\"true\"",
        "<placeholder name=\"body\" generator=\"test:default\"><label"
            + " text=\"test.no\"/></placeholder>");
    write(
        "child",
        "extends=\"test:base\" title=\"test.child\""
            + " block-entity=\"zone.moddev.mc.lib.gui.ScreenLoaderTest$Press\"",
        "");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ScreenLoader.load(
                List.of(new SourceRoot("test", directory, getClass().getClassLoader()))));
  }
}
