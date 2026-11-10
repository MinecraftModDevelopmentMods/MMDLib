package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.xml.namespace.QName;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.*;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.model.elements.LabelElement;
import zone.moddev.mc.lib.gui.value.ValueType;
import zone.moddev.mc.lib.gui.value.ValueTypes;

class ScreenTagsTest {
  private static final String MOD = "https://example.test/machine-ui";
  private static final ResourceLocation ID = ResourceLocation.parse("example:tag_test");
  private static final String HEAD =
      "<screen xmlns=\""
          + ScreenReader.NAMESPACE
          + "\" xmlns:mod=\""
          + MOD
          + "\" title=\"test\" block-entity=\""
          + ScreenLoaderTest.Press.class.getName()
          + "\">";

  record Counter(Common common, int limit, List<Element> children) implements Element {
    public int[] measure(TextMeasure measure) {
      return new int[] {20, 10};
    }

    public Set<ValueType<?>> valueKinds(WidgetTypes widgets) {
      return Set.of(ValueTypes.WHOLE);
    }
  }

  record Option(int value) {}

  private ScreenTag<Counter> counter() {
    return ScreenTag.element(
            Counter.class, x -> new Counter(x.common(), x.integer("limit"), x.children()))
        .attribute("limit", ScreenAttribute.defaulted("xs:int", "7"))
        .children("widget", 0, -1)
        .build();
  }

  private Screen read(ScreenTags tags, String body) throws IOException {
    return document(tags, HEAD + body + "</screen>");
  }

  private Screen document(ScreenTags tags, String xml) throws IOException {
    return ScreenReader.read(ID, "mod-screen.xml", stream(xml), new WidgetTypes(), tags);
  }

  private static InputStream stream(String text) {
    return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
  }

  private static ScreenReader.Node node(
      String name,
      Map<String, String> attributes,
      List<ScreenReader.Node> children,
      String source,
      int line) {
    return new ScreenReader.Node(name, attributes, children, source, line);
  }

  @Test
  void namespacedTagsUseDefaultsCommonAttributesChildrenAndValueContracts() throws Exception {
    var tags = ScreenTags.builtins();
    tags.register(new QName(MOD, "counter"), counter());
    var screen =
        read(
            tags,
            """
            <vbox><mod:counter id="counter" value="#getProgress" tooltip="{machineId}.tip" tooltip-fallback="Counter">
              <label text="child"/>
            </mod:counter></vbox>
            """);
    var counter = (Counter) screen.find("counter").orElseThrow();
    assertEquals(7, counter.limit());
    assertInstanceOf(LabelElement.class, counter.children().getFirst());
    assertEquals("example:tag_test.tip", counter.common().tooltip().orElseThrow().key());
    assertEquals(
        ValueTypes.WHOLE,
        ScreenBinder.bind(screen, getClass().getClassLoader()).values().get(counter).kind());
    var alias =
        document(tags, HEAD.replace("xmlns:mod=", "xmlns:other=") + "<other:counter/></screen>");
    assertEquals(7, ((Counter) alias.body().getFirst()).limit());
    var failure =
        assertThrows(
            ScreenException.class,
            () ->
                ScreenBinder.bind(
                    read(tags, "<mod:counter value=\"#isAutoEject\"/>"),
                    getClass().getClassLoader()));
    assertTrue(failure.getMessage().contains("mod-screen.xml"));
  }

  @Test
  void customDataChildrenAndPlacementGroupsComeFromTheRegistry() throws Exception {
    var tags = ScreenTags.builtins();
    var option = new QName(MOD, "option");
    tags.register(
        option,
        ScreenTag.data(Option.class, x -> new Option(x.integer("value")))
            .attribute("value", ScreenAttribute.required("xs:int"))
            .build());
    tags.register(
        new QName(MOD, "picker"),
        ScreenTag.element(
                Counter.class,
                x ->
                    new Counter(x.common(), x.children(Option.class).getFirst().value(), List.of()))
            .children(option, 1, 1)
            .build());
    tags.register(
        new QName(MOD, "dock"),
        ScreenTag.element(Counter.class, x -> new Counter(x.common(), 0, List.of()))
            .groups("dock")
            .build());
    var screen = read(tags, "<mod:picker><mod:option value=\"12\"/></mod:picker><mod:dock/>");
    assertEquals(12, ((Counter) screen.body().getFirst()).limit());
    assertInstanceOf(Counter.class, screen.tabs().getFirst());
    assertThrows(ScreenException.class, () -> read(tags, "<mod:option value=\"1\"/>"));
    assertThrows(
        ScreenException.class,
        () -> read(tags, "<mod:picker><label text=\"wrong\"/></mod:picker>"));
    assertThrows(ScreenException.class, () -> read(tags, "<mod:picker/>"));
    assertThrows(ScreenException.class, () -> read(tags, "<vbox><mod:dock/></vbox>"));
  }

  @Test
  void strictSchemasRejectUnknownNamesAttributesValuesTextAndExternalEntities() throws Exception {
    var tags = ScreenTags.builtins();
    tags.register(new QName(MOD, "counter"), counter());
    for (var xml :
        List.of(
            "<mod:missing/>",
            "<mod:counter surprise=\"1\"/>",
            "<mod:counter limit=\"no\"/>",
            "<mod:counter>text</mod:counter>",
            "<bad:counter xmlns:bad=\"https://unregistered.test\"/>"))
      assertThrows(ScreenException.class, () -> read(tags, xml), xml);
    assertThrows(
        ScreenException.class,
        () ->
            document(
                tags,
                "<!DOCTYPE screen [<!ENTITY x SYSTEM 'file:///missing'>]>"
                    + HEAD
                    + "<mod:counter/></screen>"));
    assertThrows(
        ScreenException.class,
        () ->
            read(
                tags,
                "<mod:counter><xi:include xmlns:xi=\"http://www.w3.org/2001/XInclude\""
                    + " href=\"file:///missing\"/></mod:counter>"));
    // The schema-location hint cannot replace the registered contract or trigger external loading.
    assertEquals(
        7,
        ((Counter)
                read(
                        tags,
                        "<mod:counter xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                            + " xsi:schemaLocation=\""
                            + MOD
                            + " file:///missing\"/>")
                    .body()
                    .getFirst())
            .limit());
  }

  @Test
  void registrationsCanAliasBuiltinsIncludingTheDocumentRoot() throws Exception {
    var tags = ScreenTags.builtins();
    tags.register(new QName(MOD, "panel"), tags.require(ScreenTags.name("screen")));
    tags.register(new QName(MOD, "column"), tags.require(ScreenTags.name("vbox")));
    tags.register(new QName(MOD, "filter"), tags.require(ScreenTags.name("ghost-slot")));
    var screen =
        document(
            tags,
            HEAD.replace("<screen ", "<mod:panel ")
                + "<mod:column><mod:filter index=\"0\"/></mod:column></mod:panel>");
    var box = (zone.moddev.mc.lib.gui.model.containers.BoxElement) screen.body().getFirst();
    assertTrue(box.vertical());
    assertInstanceOf(
        zone.moddev.mc.lib.gui.model.elements.GhostSlotElement.class, box.children().getFirst());
  }

  @Test
  void registrationReplacementAndFreezeAlsoControlTheSchema() throws Exception {
    var tags = ScreenTags.builtins();
    read(tags, "<label text=\"before\"/>"); // Compile first.
    tags.register("mod-counter", counter());
    assertInstanceOf(Counter.class, read(tags, "<mod-counter/>").body().getFirst());
    assertThrows(IllegalArgumentException.class, () -> tags.register("mod-counter", counter()));
    tags.replace(ScreenTags.name("label"), counter());
    assertInstanceOf(Counter.class, read(tags, "<label limit=\"9\"/>").body().getFirst());
    assertThrows(ScreenException.class, () -> read(tags, "<label text=\"old-contract\"/>"));
    tags.freeze();
    assertThrows(IllegalStateException.class, () -> tags.register("late", counter()));
    assertThrows(
        IllegalStateException.class, () -> tags.replace(ScreenTags.name("label"), counter()));
  }

  @Test
  void inheritanceGeneratorsAndEmbeddedTreesPreserveNamespaceAndSource() throws Exception {
    var tags = ScreenTags.builtins();
    // A local name matching a template directive is still an ordinary mod element.
    tags.register(new QName(MOD, "placeholder"), counter());
    var baseId = ResourceLocation.parse("example:base");
    var placeholder = node("placeholder", Map.of("name", "body"), List.of(), "base.xml", 4);
    var base =
        node(
            "screen",
            Map.of("abstract", "true", "block-entity", ScreenLoaderTest.Press.class.getName()),
            List.of(placeholder),
            "base.xml",
            1);
    var mod =
        new ScreenReader.Node("placeholder", Map.of("id", "mod"), List.of(), "child.xml", 19, MOD);
    var fill = node("fill", Map.of("name", "body"), List.of(mod), "child.xml", 18);
    var child =
        node(
            "screen",
            Map.of("extends", baseId.toString(), "title", "child"),
            List.of(fill),
            "child.xml",
            1);
    var screen =
        ScreenLoader.read(
                Map.of(baseId, base, ID, child),
                new WidgetTypes(),
                ScreenFillGenerator.UNAVAILABLE,
                tags)
            .get(ID);
    var element = screen.find("mod").orElseThrow();
    assertInstanceOf(Counter.class, element);
    assertEquals("child.xml", screen.source(element.common()));
    assertEquals(19, element.common().line());
    var generated = placeholder.withChildren(List.of());
    generated =
        node(
            "placeholder",
            Map.of("name", "body", "generator", "example:counter"),
            List.of(),
            "base.xml",
            4);
    var generatedRoot =
        node(
            "screen",
            Map.of("title", "generated", "block-entity", ScreenLoaderTest.Press.class.getName()),
            List.of(generated),
            "base.xml",
            1);
    assertInstanceOf(
        Counter.class,
        ScreenLoader.read(
                Map.of(ID, generatedRoot), new WidgetTypes(), (id, template) -> List.of(mod), tags)
            .get(ID)
            .body()
            .getFirst());
    var machine =
        ScreenReader.parseMachine(
            "machine.xml",
            stream(
                "<machine xmlns=\"https://moddev.zone/mmdlib/machine\" xmlns:mod=\""
                    + MOD
                    + "\"><screen title=\"embedded\" block-entity=\""
                    + ScreenLoaderTest.Press.class.getName()
                    + "\"><mod:placeholder/></screen></machine>"));
    var embedded = machine.children().getFirst();
    assertInstanceOf(
        Counter.class,
        ScreenLoader.read(
                Map.of(ID, embedded), new WidgetTypes(), ScreenFillGenerator.UNAVAILABLE, tags)
            .get(ID)
            .body()
            .getFirst());
  }
}
