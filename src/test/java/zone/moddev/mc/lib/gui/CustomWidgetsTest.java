package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.client.WidgetRenderers;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.BoundValue.Kind;
import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;

class CustomWidgetsTest {
  private static final ResourceLocation ID = ResourceLocation.parse("example:dial");
  private static final String HEAD =
      """
      <screen xmlns="https://moddev.zone/mmdlib/screen" title="screen.example.test" inventory="none"
              block-entity="zone.moddev.mc.lib.gui.ScreenLoaderTest$Press">
      """;

  private WidgetTypes registry() {
    var types = new WidgetTypes();
    types.register(
        ID,
        new WidgetType(
            Set.of(Kind.WHOLE),
            widget -> {
              if (!Set.of("color", "style").containsAll(widget.properties().keySet()))
                throw new IllegalArgumentException("Unsupported dial property");
              if (widget.common().value().isEmpty() || widget.common().max().isEmpty())
                throw new IllegalArgumentException("Dial needs value and max");
            }));
    return types;
  }

  private Screen read(WidgetTypes types, String body) throws Exception {
    return ScreenReader.read(
        ID,
        "custom.xml",
        new ByteArrayInputStream((HEAD + body + "</screen>").getBytes(StandardCharsets.UTF_8)),
        types);
  }

  private String widget(String attributes, String properties) {
    return "<custom id=\"dial\" type=\"example:dial\" width=\"1.5s\" height=\"18\" "
        + attributes
        + ">"
        + properties
        + "</custom>";
  }

  private static final String VALUES = "value=\"#getProgress\" max=\"#getProgressMax\"";

  @Test
  void composesBindsAndPreservesCommonAttributes() throws Exception {
    var types = registry();
    var screen =
        read(
            types,
            "<hbox spacing=\"4\">"
                + widget(
                    VALUES
                        + " on-click=\"example:toggle\" on-hover=\"example:inspect\""
                        + " tooltip=\"screen.example.tip\"",
                    "<property name=\"color\" value=\"#ABCDEF\"/>")
                + "<slot index=\"0\"/></hbox>");
    var custom = (Element.Custom) screen.find("dial").orElseThrow();
    var bound = ScreenBinder.bind(screen, getClass().getClassLoader(), types);
    assertEquals(Kind.WHOLE, bound.values().get(custom).kind());
    assertEquals(Kind.WHOLE, bound.maxima().get(custom).kind());
    assertEquals(Map.of("color", "#ABCDEF"), custom.properties());
    assertThrows(
        UnsupportedOperationException.class, () -> custom.properties().put("color", "red"));
    assertTrue(custom.children().isEmpty());
    assertEquals(3, screen.elements().size(), "Properties must not consume menu element indexes");
    assertEquals("example:toggle", custom.common().clicks().click().orElseThrow().toString());
    assertEquals("example:inspect", custom.common().onHover().orElseThrow().toString());
    var layout = ScreenLayout.of(screen, TextMeasure.NONE);
    var rect = layout.bounds(custom);
    assertEquals(27, rect.width());
    assertEquals(18, rect.height());
    assertEquals(rect.right() + 4, layout.bounds(screen.elements().getLast()).x());
  }

  @Test
  void fingerprintsIncludeCustomConfigurationInCanonicalOrder() throws Exception {
    var types = registry();
    String color = "<property name=\"color\" value=\"#ABCDEF\"/>";
    String style = "<property name=\"style\" value=\"ring\"/>";
    var a = read(types, widget(VALUES, color + style));
    var b = read(types, widget(VALUES, style + color));
    assertEquals(a.fingerprint(), b.fingerprint());
    assertNotEquals(
        a.fingerprint(),
        read(types, widget(VALUES, color.replace("ABCDEF", "000000") + style)).fingerprint());
    assertNotEquals(
        a.fingerprint(),
        read(types, widget(VALUES, color + style).replace("1.5s", "2s")).fingerprint());
    // Map.toString would turn both configurations into {color=a, style=b}.
    assertNotEquals(
        read(types, widget(VALUES, "<property name=\"color\" value=\"a, style=b\"/>"))
            .fingerprint(),
        read(
                types,
                widget(
                    VALUES,
                    "<property name=\"color\" value=\"a\"/><property name=\"style\" value=\"b\"/>"))
            .fingerprint());
  }

  @Test
  void rejectsBadDefinitionsWithSourceLocations() {
    var types = registry();
    for (var body :
        new String[] {
          widget(VALUES, "").replace("example:dial", "example:missing"),
          widget(VALUES, "<property name=\"typo\" value=\"x\"/>"),
          widget(
              VALUES,
              "<property name=\"color\" value=\"a\"/><property name=\"color\" value=\"b\"/>"),
          widget("", ""),
          widget(VALUES, "").replace("1.5s", "0"),
          widget(VALUES, "").replace("1.5s", "1025"),
          widget(VALUES + " typo=\"x\"", ""),
          widget(VALUES, "<slot index=\"0\"/>"),
          widget(VALUES, "<property name=\"color\"/>")
        }) {
      var error = assertThrows(ScreenException.class, () -> read(types, body), body);
      assertTrue(error.getMessage().contains("custom.xml:3:"), error.getMessage());
    }
  }

  @Test
  void rejectsWrongGetterKindsBeforeOpening() throws Exception {
    var types = registry();
    var screen = read(types, widget("value=\"#isAutoEject\" max=\"#getProgressMax\"", ""));
    var error =
        assertThrows(
            ScreenException.class,
            () -> ScreenBinder.bind(screen, getClass().getClassLoader(), types));
    assertTrue(error.getMessage().contains("whole number"));
    var badMax = read(types, widget("value=\"#getProgress\" max=\"#isAutoEject\"", ""));
    assertThrows(
        ScreenException.class, () -> ScreenBinder.bind(badMax, getClass().getClassLoader(), types));
  }

  @Test
  void supportsDecorationsWithoutValuesAndForbidsUndeclaredBindings() throws Exception {
    var types = new WidgetTypes();
    types.register(ID, new WidgetType(Set.of(), widget -> {}));
    var screen = read(types, widget("", ""));
    assertTrue(ScreenBinder.bind(screen, getClass().getClassLoader(), types).values().isEmpty());
    assertThrows(ScreenException.class, () -> read(types, widget(VALUES, "")));
    assertThrows(ScreenException.class, () -> read(types, widget("max=\"#getProgressMax\"", "")));
  }

  @Test
  void registriesRejectDuplicatesAndLateRegistration() {
    var types = registry();
    var type = types.require(ID);
    assertThrows(IllegalArgumentException.class, () -> types.register(ID, type));
    types.freeze();
    assertSame(type, types.require(ID));
    assertThrows(
        IllegalStateException.class,
        () -> types.register(ResourceLocation.parse("example:late"), type));
    var renderers = new WidgetRenderers();
    renderers.register(ID, context -> {});
    assertThrows(IllegalArgumentException.class, () -> renderers.register(ID, context -> {}));
    renderers.freeze(types);
    assertThrows(
        IllegalStateException.class,
        () -> renderers.register(ResourceLocation.parse("example:late"), context -> {}));
  }

  @Test
  void missingRenderersAndUnknownRendererTypesFailExplicitly() throws Exception {
    var types = registry();
    var screen = read(types, widget(VALUES, ""));
    var renderers = new WidgetRenderers();
    var error = assertThrows(ScreenException.class, () -> renderers.validate(screen));
    assertTrue(error.getMessage().contains("custom.xml:3: Missing widget renderer: example:dial"));
    renderers.register(ID, context -> {});
    assertDoesNotThrow(() -> renderers.validate(screen));
    assertThrows(IllegalArgumentException.class, () -> renderers.freeze(new WidgetTypes()));
  }
}
