package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.load.ScreenLoader;
import zone.moddev.mc.lib.gui.model.Element;

class ScreenInheritanceTest {
  @TempDir Path directory;

  private static final String NS = " xmlns=\"https://moddev.zone/mmdlib/screen\"";
  private static final String HOST =
      " block-entity=\"zone.moddev.mc.lib.gui.ScreenLoaderTest$Press\"";

  private Path write(String id, String attributes, String body) throws Exception {
    var parts = id.split(":", 2);
    var file = directory.resolve(parts[0]).resolve("screen").resolve(parts[1] + ".xml");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "<screen" + NS + " " + attributes + ">\n" + body + "\n</screen>");
    return file;
  }

  private void base(String body) throws Exception {
    write("test:base", "abstract=\"true\"", body);
  }

  private void child(String body) throws Exception {
    write("test:child", "extends=\"test:base\" title=\"test.child\"" + HOST, body);
  }

  private SourceRoot source(String namespace) {
    return new SourceRoot(namespace, directory.resolve(namespace), getClass().getClassLoader());
  }

  private Map<ResourceLocation, ScreenBinder.Bound> load() {
    return ScreenLoader.load(List.of(source("test")));
  }

  @Test
  void poweredTemplateSuppliesItsEntityAndCommonBindings() throws Exception {
    write(
        "test:powered",
        "extends=\"mmdlib:base_powered_machine\" title=\"test.powered\"",
        "<fill name=\"machine\"><progress id=\"progress\" value=\"#getProgress\""
            + " max=\"#getTime\"/></fill>");
    var bound =
        ScreenLoader.load(
                List.of(
                    source("test"),
                    new SourceRoot(
                        "mmdlib",
                        Path.of("src/main/resources/mmdlib"),
                        getClass().getClassLoader())))
            .get(ResourceLocation.parse("test:powered"));
    assertEquals(
        "zone.moddev.mc.lib.machine.neoforge.BasePoweredMachine", bound.screen().blockEntity());
    for (var id : List.of("energy", "progress")) {
      var element = bound.screen().find(id).orElseThrow();
      assertTrue(bound.values().containsKey(element));
      assertTrue(bound.maxima().containsKey(element));
    }
    var button = (Element.Button) bound.screen().find("toggle_0").orElseThrow();
    assertTrue(button.processorControl());
    assertTrue(button.processorKey().isEmpty());
    var info = (Element.Popout) bound.screen().find("info").orElseThrow();
    assertEquals("test:powered.info", info.text().orElseThrow().key());
    assertTrue(bound.values().containsKey(info));
  }

  @Test
  void translationTokensUseEachConcreteChildIdAcrossNamespacesAndInheritanceChains()
      throws Exception {
    write(
        "library:base",
        "abstract=\"true\"",
        """
        <placeholder name="body">
          <label id="label" text="{machineId}.label" text-fallback="Literal {machineId}"
              tooltip="{machineId}.tip" tooltip-fallback="{value} / {max}"/>
          <button id="button" value="#getProgress" icons="mmdlib:off mmdlib:on"
              value-labels="{machineId}.off {machineId}.on" tooltip="{machineId}.state"/>
        </placeholder>
        <placeholder name="info"><popout id="info" kind="info" text="{machineId}.info"/></placeholder>
        """);
    write("test:middle", "abstract=\"true\" extends=\"library:base\"", "");
    for (var name : List.of("first", "nested/second"))
      write("test:" + name, "extends=\"test:middle\" title=\"{machineId}.title\"" + HOST, "");
    var screens = ScreenLoader.load(List.of(source("library"), source("test")));
    for (var name : List.of("first", "nested/second")) {
      var id = "test:" + name;
      var screen = screens.get(ResourceLocation.parse(id)).screen();
      assertEquals(id + ".title", screen.title().key());
      var label = (Element.Label) screen.find("label").orElseThrow();
      assertEquals(id + ".label", label.text().key());
      assertEquals("Literal {machineId}", label.text().fallback().orElseThrow());
      assertEquals(id + ".tip", label.common().tooltip().orElseThrow().key());
      assertEquals(
          "{value} / {max}", label.common().tooltip().orElseThrow().fallback().orElseThrow());
      var button = (Element.Button) screen.find("button").orElseThrow();
      assertEquals(
          List.of(id + ".off", id + ".on"),
          button.valueLabels().stream().map(zone.moddev.mc.lib.gui.model.Text::key).toList());
      var info = (Element.Popout) screen.find("info").orElseThrow();
      assertEquals(id + ".info", info.text().orElseThrow().key());
      assertTrue(screen.source(info.common()).endsWith("base.xml"));
    }
    write(
        "test:custom",
        "extends=\"test:middle\" title=\"test.custom\"" + HOST,
        "<fill name=\"info\"><popout id=\"info\" kind=\"info\" text=\"custom.info\"/></fill>");
    var custom =
        ScreenLoader.load(List.of(source("library"), source("test")))
            .get(ResourceLocation.parse("test:custom"));
    assertEquals(
        "custom.info",
        ((Element.Popout) custom.screen().find("info").orElseThrow()).text().orElseThrow().key());
  }

  @Test
  void baseProcessorControlNeedsNoMachineSpecificKeyOrFill() throws Exception {
    base(
        "<label text=\"test.machine\"/><inventory-controls><processor-button"
            + " id=\"toggle\"/></inventory-controls>");
    child("");
    var screen =
        ScreenLoader.read(List.of(source("test")), new zone.moddev.mc.lib.gui.WidgetTypes())
            .get(ResourceLocation.parse("test:child"));
    var button = (Element.Button) screen.find("toggle").orElseThrow();
    assertTrue(button.processorControl());
    assertTrue(button.processorKey().isEmpty());
    assertTrue(screen.source(button.common()).endsWith("base.xml"));
  }

  private String failure(String message) {
    var error = assertThrows(ScreenException.class, this::load);
    assertTrue(error.getMessage().contains(message), error.getMessage());
    return error.getMessage();
  }

  @Test
  void composesFrameAndBindsInheritedGettersAgainstConcreteHost() throws Exception {
    base(
        """
        <hbox spacing="6">
          <placeholder name="machine" required="true"/>
          <energy-gauge id="energy" length="fill"/>
        </hbox>
        <player-inventory/>
        """);
    child(
        """
        <fill name="machine">
          <slot id="input" index="2"/>
          <progress value="#getProgress" max="#getProgressMax"/>
          <slot id="output" index="5"/>
        </fill>
        """);
    var loaded = load();
    assertEquals(1, loaded.size());
    var bound = loaded.get(ResourceLocation.parse("test:child"));
    var screen = bound.screen();
    var row = (Element.Box) screen.body().getFirst();
    assertEquals(4, row.children().size());
    assertEquals(2, ((Element.Slot) row.children().getFirst()).index());
    assertEquals(5, ((Element.Slot) row.children().get(2)).index());
    var energy = screen.find("energy").orElseThrow();
    assertTrue(bound.values().containsKey(energy));
    assertTrue(bound.maxima().containsKey(energy));
    assertTrue(screen.playerInventory());
    assertTrue(screen.source(energy.common()).endsWith("base.xml"));
    assertTrue(screen.source(row.children().getFirst().common()).endsWith("child.xml"));
  }

  @Test
  void resolvesAcrossNamespacesAndDiscoveryOrderUsingChildClassLoader() throws Exception {
    write(
        "other:z_base",
        "abstract=\"1\"",
        "<placeholder name=\"body\"><label text=\"test.default\"/></placeholder>");
    write("test:a_child", "extends=\"other:z_base\" title=\"test.child\"" + HOST, "");
    var inaccessible = new ClassLoader(null) {};
    var loaded =
        ScreenLoader.load(
            List.of(
                source("test"), new SourceRoot("other", directory.resolve("other"), inaccessible)));
    assertEquals(1, loaded.size());
    assertInstanceOf(
        Element.Label.class,
        loaded.get(ResourceLocation.parse("test:a_child")).screen().body().getFirst());
  }

  @Test
  void supportsChainedOverridesDefaultsAndNewPlaceholders() throws Exception {
    base(
        "<vbox><placeholder name=\"machine\" required=\"true\"/><placeholder"
            + " name=\"optional\"/></vbox>");
    write(
        "test:middle",
        "abstract=\"true\" extends=\"test:base\"",
        """
        <fill name="machine"><hbox><placeholder name="input" required="true"/><slot index="1"/></hbox></fill>
        <fill name="optional"><label id="default" text="test.default"/></fill>
        """);
    write(
        "test:child",
        "extends=\"test:middle\" title=\"test.child\"" + HOST,
        "<fill name=\"input\"><slot index=\"0\"/></fill><fill name=\"optional\"/>");
    var screen = load().get(ResourceLocation.parse("test:child")).screen();
    assertEquals(2, screen.elements().stream().filter(e -> e instanceof Element.Slot).count());
    assertTrue(screen.find("default").isEmpty());
  }

  @Test
  void inheritsInventorySettingUnlessExplicitlyOverridden() throws Exception {
    write("test:base", "abstract=\"true\" inventory=\"none\"", "<label text=\"test.body\"/>");
    child("");
    assertFalse(load().get(ResourceLocation.parse("test:child")).screen().playerInventory());
    write(
        "test:child",
        "extends=\"test:base\" inventory=\"default\" title=\"test.child\"" + HOST,
        "");
    assertTrue(load().get(ResourceLocation.parse("test:child")).screen().playerInventory());
  }

  @Test
  void validatesReferencesAcrossBaseAndChildAfterComposition() throws Exception {
    base("<vbox><placeholder name=\"body\"/><side-config groups=\"input\"/></vbox>");
    child("<fill name=\"body\"><slot id=\"input\" index=\"0\"/></fill>");
    assertEquals(1, load().size());
  }

  @Test
  void missingRequiredFillNamesBaseFileAndLine() throws Exception {
    base("<placeholder name=\"machine\" required=\"true\"/>");
    child("");
    assertTrue(failure("Missing required fill: machine").contains("base.xml:2"));
  }

  @Test
  void rejectsUnknownAndDuplicateFills() throws Exception {
    base("<placeholder name=\"machine\"/>");
    child("<fill name=\"typo\"/>");
    assertTrue(failure("Unknown placeholder: typo").contains("child.xml:2"));
    child("<fill name=\"machine\"/><fill name=\"machine\"/>");
    failure("Duplicate fill: machine");
  }

  @Test
  void rejectsAmbiguousAndContradictoryPlaceholdersEvenInUnusedBases() throws Exception {
    base("<vbox><placeholder name=\"x\"/><placeholder name=\"x\"/></vbox>");
    failure("Duplicate placeholder: x");
    base("<placeholder name=\"x\" required=\"true\"><label text=\"test.x\"/></placeholder>");
    failure("required placeholder cannot also have default");
  }

  @Test
  void rejectsFillHiddenByOuterReplacement() throws Exception {
    base("<placeholder name=\"outer\"><placeholder name=\"inner\"/></placeholder>");
    child("<fill name=\"outer\"><label text=\"test.x\"/></fill><fill name=\"inner\"/>");
    failure("Fill targets a replaced placeholder: inner");
  }

  @Test
  void rejectsUnknownBaseAndCyclesEvenWithoutConcreteScreens() throws Exception {
    child("");
    failure("Unknown base screen: test:base");
    write("test:base", "abstract=\"true\" extends=\"test:other\"", "");
    write("test:other", "abstract=\"true\" extends=\"test:base\"", "");
    failure("Screen inheritance cycle");
  }

  @Test
  void rejectsMixedOrUnattachedFillsAndMissingConcreteMetadata() throws Exception {
    base("<placeholder name=\"machine\"/>");
    child("<slot index=\"0\"/>");
    failure("supplies content through <fill> only");
    write("test:child", "title=\"test.child\"" + HOST, "<fill name=\"machine\"/>");
    failure("<fill> requires extends");
    write("test:child", "extends=\"test:base\"" + HOST, "");
    failure("requires its own title");
    write("test:child", "extends=\"test:base\" title=\"test.child\"", "");
    failure("requires its own block-entity");
  }

  @Test
  void rejectsDuplicateSlotsAndIdsAcrossFiles() throws Exception {
    base("<vbox><slot index=\"0\" id=\"fixed\"/><placeholder name=\"machine\"/></vbox>");
    child("<fill name=\"machine\"><slot index=\"0\"/></fill>");
    assertTrue(failure("Slot 0 is placed twice").contains("child.xml:2"));
    child("<fill name=\"machine\"><slot index=\"1\" id=\"fixed\"/></fill>");
    failure("Duplicate id: fixed");
  }

  @Test
  void validatesExpandedStructureAndLayoutAtTheOriginalLocation() throws Exception {
    base("<hbox><placeholder name=\"machine\"/></hbox>");
    child("<fill name=\"machine\"><side-tab><slot index=\"0\"/></side-tab></fill>");
    assertTrue(failure("side-tab").contains("child.xml:2"));
    base("<vbox><placeholder name=\"machine\"/><energy-gauge length=\"fill\"/></vbox>");
    child("<fill name=\"machine\"><slot index=\"0\"/></fill>");
    assertTrue(failure("belongs directly in an <hbox>").contains("base.xml:2"));
  }

  @Test
  void inheritedBindingErrorsNameBaseAndDoNotMutateOtherChildren() throws Exception {
    base("<vbox><energy-gauge value=\"#missing\"/><placeholder name=\"machine\"/></vbox>");
    child("<fill name=\"machine\"><slot index=\"0\"/></fill>");
    assertTrue(failure("missing").contains("base.xml:2"));
    base("<placeholder name=\"machine\"/>");
    write(
        "test:second",
        "extends=\"test:base\" title=\"test.second\"" + HOST,
        "<fill name=\"machine\"><slot index=\"5\"/></fill>");
    var loaded = load();
    assertEquals(
        0,
        ((Element.Slot) loaded.get(ResourceLocation.parse("test:child")).screen().body().getFirst())
            .index());
    assertEquals(
        5,
        ((Element.Slot)
                loaded.get(ResourceLocation.parse("test:second")).screen().body().getFirst())
            .index());
  }

  @Test
  void fingerprintsExcludeMachineLocalSourcePathsButIncludeInheritedChanges() throws Exception {
    base("<label text=\"test.first\"/>");
    child("");
    var first = load().get(ResourceLocation.parse("test:child")).screen().fingerprint();
    var oldDirectory = directory;
    directory = directory.resolve("relocated");
    base("<label text=\"test.first\"/>");
    child("");
    assertEquals(first, load().get(ResourceLocation.parse("test:child")).screen().fingerprint());
    base("<label text=\"test.changed\"/>");
    assertNotEquals(first, load().get(ResourceLocation.parse("test:child")).screen().fingerprint());
    directory = oldDirectory;
  }
}
