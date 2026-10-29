package zone.moddev.mc.lib.gui.load;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import net.minecraft.resources.ResourceLocation;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Element.*;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.ValueRef;

/**
 * Reads a screen file into a {@link Screen}. The file is validated against {@code
 * mmdlib/schema/screen.xsd} (structure, attribute names and value formats), then checked for what a
 * schema cannot express: unique IDs and slot indexes, references between elements, required
 * pairings, and each element's needs. Errors name the file and line.
 *
 * <p>The parser is hardened: DOCTYPE declarations, external entities and XInclude are refused, so a
 * screen file cannot read other files or expand entities.
 */
public final class ScreenReader {
  /** The XML namespace of screen files. */
  public static final String NAMESPACE = "https://moddev.zone/mmdlib/screen";

  private static final Schema SCHEMA = loadSchema();

  private ScreenReader() {}

  /**
   * Reads one screen file.
   *
   * @param id the screen ID, from the file's path
   * @param source names the file in error messages
   * @throws ScreenException if the file is not well-formed, breaks the schema, or fails a check
   * @throws IOException if the stream cannot be read
   */
  public static Screen read(ResourceLocation id, String source, InputStream input)
      throws IOException {
    Node root = parse(source, input);
    var screen = new Builder(source).screen(id, root);
    check(screen);
    return screen;
  }

  // Parsing

  /** One parsed element, with its attributes as written (schema defaults filled in). */
  private record Node(String name, Map<String, String> attributes, List<Node> children, int line) {}

  private static Node parse(String source, InputStream input) throws IOException {
    try {
      var factory = SAXParserFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setXIncludeAware(false);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setSchema(SCHEMA);
      var handler = new TreeHandler(source);
      var parser = factory.newSAXParser();
      parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      parser.parse(new InputSource(input), handler);
      return handler.root;
    } catch (SAXParseException ex) {
      throw new ScreenException(source, ex.getLineNumber(), ex.getMessage(), ex);
    } catch (SAXException | ParserConfigurationException ex) {
      throw new ScreenException(source, 0, ex.getMessage(), ex);
    }
  }

  private static Schema loadSchema() {
    try (var stream = ScreenReader.class.getResourceAsStream("/mmdlib/schema/screen.xsd")) {
      if (stream == null) throw new IllegalStateException("Missing mmdlib/schema/screen.xsd");
      var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      return factory.newSchema(new StreamSource(stream));
    } catch (IOException | SAXException ex) {
      throw new IllegalStateException("Cannot load the screen schema", ex);
    }
  }

  private static final class TreeHandler extends DefaultHandler {
    private final String source;
    private final List<List<Node>> open = new ArrayList<>();
    private final List<Node> stack = new ArrayList<>();
    private Locator locator;
    private Node root;

    TreeHandler(String source) {
      this.source = source;
    }

    @Override
    public void setDocumentLocator(Locator locator) {
      this.locator = locator;
    }

    @Override
    public void startElement(String uri, String local, String qName, Attributes attributes)
        throws SAXException {
      if (!NAMESPACE.equals(uri))
        throw new SAXParseException(
            "Element <" + qName + "> is not in the screen namespace " + NAMESPACE, locator);
      var values = new LinkedHashMap<String, String>();
      for (int i = 0; i < attributes.getLength(); i++)
        if (attributes.getURI(i).isEmpty())
          values.put(attributes.getLocalName(i), attributes.getValue(i));
      var children = new ArrayList<Node>();
      var node = new Node(local, values, children, locator.getLineNumber());
      if (stack.isEmpty()) root = node;
      else open.getLast().add(node);
      stack.add(node);
      open.add(children);
    }

    @Override
    public void endElement(String uri, String local, String qName) {
      stack.removeLast();
      open.removeLast();
    }

    @Override
    public void error(SAXParseException ex) throws SAXException {
      throw ex; // Schema violations are errors, not warnings.
    }

    @Override
    public void fatalError(SAXParseException ex) throws SAXException {
      throw ex;
    }
  }

  // Conversion to the model

  private static final class Builder {
    private final String source;

    Builder(String source) {
      this.source = source;
    }

    Screen screen(ResourceLocation id, Node root) {
      var body = new ArrayList<Element>();
      var tabs = new ArrayList<Element>();
      for (var child : root.children()) {
        var element = element(child);
        if (element instanceof SideTab || element instanceof Popout) tabs.add(element);
        else body.add(element);
      }
      return new Screen(
          id,
          source,
          text(root, "title").orElseThrow(),
          root.attributes().get("block-entity"),
          !"none".equals(root.attributes().get("inventory")),
          body,
          tabs);
    }

    private List<Element> children(Node node) {
      return node.children().stream().map(this::element).toList();
    }

    private Element element(Node node) {
      var c = common(node);
      return switch (node.name()) {
        case "vbox", "hbox" ->
            new Box(
                c,
                node.name().equals("vbox"),
                size(node, "padding", 0),
                size(node, "spacing", 0),
                enumValue(Align.class, node, "align"),
                children(node));
        case "spacer" -> new Spacer(c);
        case "group" ->
            new Group(
                c,
                text(node, "label"),
                color(node, "tint"),
                size(node, "padding", 0),
                children(node));
        case "slot" ->
            new Slot(
                c,
                integer(node, "index"),
                enumValue(SlotSize.class, node, "size"),
                enumValue(SlotKind.class, node, "kind"),
                color(node, "tint"),
                id(node, "icon"));
        case "gauge" ->
            new Gauge(
                c,
                enumValue(Orientation.class, node, "orientation"),
                length(node, 8),
                color(node, "color"));
        case "meter" ->
            new Meter(
                c,
                enumValue(Orientation.class, node, "orientation"),
                integer(node, "thickness"),
                length(node, 4),
                color(node, "color").orElseThrow(),
                size(node, "segment", 1),
                size(node, "gap", 0),
                color(node, "gap-color"));
        case "progress" ->
            new Progress(c, id(node, "icon"), enumValue(Direction.class, node, "direction"));
        case "label" -> new Label(c, text(node, "text").orElseThrow());
        case "button" ->
            new Button(
                c,
                text(node, "text"),
                id(node, "icon"),
                Optional.ofNullable(node.attributes().get("shows")).map(ViewRef::parse));
        case "checkbox" -> new Checkbox(c, text(node, "text").orElseThrow());
        case "radio-group" ->
            new RadioGroup(
                c,
                text(node, "label"),
                node.children().stream().map(child -> (Radio) element(child)).toList());
        case "radio" -> new Radio(c, text(node, "text").orElseThrow());
        case "context" ->
            new Context(c, node.children().stream().map(child -> (View) element(child)).toList());
        case "view" -> new View(c, text(node, "title"), children(node));
        case "side-config" ->
            new SideConfig(c, List.of(node.attributes().get("groups").trim().split("\\s+")));
        case "player-inventory" -> new PlayerInventory(c);
        case "side-tab" -> new SideTab(c, enumValue(Edge.class, node, "edge"), children(node));
        case "popout" ->
            new Popout(
                c,
                enumValue(PopoutKind.class, node, "kind"),
                id(node, "icon"),
                enumValue(Edge.class, node, "edge"),
                text(node, "text"));
        default -> throw error(node, "Unknown element <" + node.name() + ">");
      };
    }

    private Common common(Node node) {
      var a = node.attributes();
      var recipes = a.get("jei-recipes");
      return new Common(
          Optional.ofNullable(a.get("id")),
          value(node, "value"),
          value(node, "max"),
          text(node, "tooltip"),
          text(node, "tooltip-shift"),
          new Common.Clicks(
              id(node, "on-click"),
              id(node, "on-right-click"),
              id(node, "on-shift-click"),
              id(node, "on-shift-right-click")),
          id(node, "on-hover"),
          Optional.ofNullable(a.get("hover-shows")),
          recipes == null || recipes.isBlank()
              ? List.of()
              : java.util.Arrays.stream(recipes.trim().split("\\s+"))
                  .map(ResourceLocation::parse)
                  .toList(),
          a.containsKey("offset-x") ? offset(node, "offset-x") : 0,
          a.containsKey("offset-y") ? offset(node, "offset-y") : 0,
          node.line());
    }

    private Optional<Text> text(Node node, String name) {
      var key = node.attributes().get(name);
      var fallback = Optional.ofNullable(node.attributes().get(name + "-fallback"));
      if (key == null) {
        if (fallback.isPresent())
          throw error(node, name + "-fallback needs " + name + " (a translation key)");
        return Optional.empty();
      }
      return Optional.of(new Text(key, fallback));
    }

    private Optional<ValueRef> value(Node node, String name) {
      return Optional.ofNullable(node.attributes().get(name)).map(ValueRef::parse);
    }

    private Optional<ResourceLocation> id(Node node, String name) {
      var value = node.attributes().get(name);
      if (value == null) return Optional.empty();
      var id = ResourceLocation.tryParse(value);
      if (id == null) throw error(node, "Invalid ID in " + name + ": " + value);
      return Optional.of(id);
    }

    private Optional<Integer> color(Node node, String name) {
      var value = node.attributes().get(name);
      if (value == null) return Optional.empty();
      long rgb = Long.parseLong(value.substring(1), 16);
      return Optional.of((int) (value.length() == 7 ? 0xFF000000L | rgb : rgb));
    }

    /**
     * A size attribute in GUI pixels: a plain number is pixels, an {@code s} suffix counts slots
     * (18 px each), rounded to the nearest pixel.
     */
    private int size(Node node, String name, int minimum) {
      int pixels = pixels(node, name);
      if (pixels < minimum || pixels > 1024)
        throw error(node, name + " must be from " + minimum + " to 1024 px, not " + pixels);
      return pixels;
    }

    private int length(Node node, int minimum) {
      return "fill".equals(node.attributes().get("length"))
          ? Element.FILL
          : size(node, "length", minimum);
    }

    private int offset(Node node, String name) {
      int pixels = pixels(node, name);
      if (Math.abs(pixels) > 64)
        throw error(node, name + " must be within 64 px either way, not " + pixels);
      return pixels;
    }

    private int pixels(Node node, String name) {
      var value = node.attributes().get(name).trim();
      try {
        if (!value.endsWith("s")) return Integer.parseInt(value);
        var slots = new java.math.BigDecimal(value.substring(0, value.length() - 1));
        return slots
            .multiply(java.math.BigDecimal.valueOf(Element.SLOT))
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .intValueExact();
      } catch (NumberFormatException | ArithmeticException ex) {
        throw error(node, name + " is too large: " + value);
      }
    }

    private int integer(Node node, String name) {
      return Integer.parseInt(node.attributes().get(name).trim());
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, Node node, String name) {
      return Enum.valueOf(
          type, node.attributes().get(name).toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
    }

    private ScreenException error(Node node, String message) {
      return new ScreenException(source, node.line(), message);
    }
  }

  // Checks a schema cannot express

  private static void check(Screen screen) {
    var source = screen.source();
    var ids = new HashMap<String, Element>();
    var slots = new HashMap<Integer, Element>();
    var contexts = new HashMap<String, Set<String>>();
    Element inventory = null;
    for (var element : screen.elements()) {
      var c = element.common();
      if (c.id().isPresent() && ids.putIfAbsent(c.id().get(), element) != null)
        throw new ScreenException(source, c.line(), "Duplicate id: " + c.id().get());
      if (element instanceof Slot slot && slots.putIfAbsent(slot.index(), element) != null)
        throw new ScreenException(
            source, c.line(), "Slot " + slot.index() + " is placed twice on this screen");
      if (element instanceof Context context) {
        if (c.id().isEmpty())
          throw new ScreenException(source, c.line(), "<context> needs an id for buttons to name");
        var views = new HashSet<String>();
        for (var view : context.views())
          if (view.common().id().isEmpty())
            throw new ScreenException(
                source, view.common().line(), "<view> needs an id for buttons to name");
          else views.add(view.common().id().get());
        contexts.put(c.id().get(), views);
      }
      if (element instanceof PlayerInventory) {
        if (inventory != null)
          throw new ScreenException(source, c.line(), "<player-inventory> may appear only once");
        inventory = element;
      }
    }
    if (inventory != null && !screen.playerInventory())
      throw new ScreenException(
          source,
          inventory.common().line(),
          "<player-inventory> cannot be placed on a screen with inventory=\"none\"");
    for (var element : screen.elements()) {
      var c = element.common();
      int line = c.line();
      if (c.hoverShows().isPresent() && !ids.containsKey(c.hoverShows().get()))
        throw new ScreenException(
            source, line, "hover-shows names no element: " + c.hoverShows().get());
      if (c.max().isPresent() && c.value().isEmpty())
        throw new ScreenException(source, line, "max needs value");
      switch (element) {
        case Button button -> {
          if (c.tooltip().isEmpty())
            throw new ScreenException(source, line, "<button> needs a tooltip saying what it does");
          if (button.text().isEmpty() && button.icon().isEmpty())
            throw new ScreenException(source, line, "<button> needs text or icon");
          if (button.shows().isPresent()) {
            var target = button.shows().get();
            var views = contexts.get(target.context());
            if (views == null)
              throw new ScreenException(
                  source, line, "shows names no context: " + target.context());
            if (!views.contains(target.view()))
              throw new ScreenException(source, line, "shows names no view: " + target);
            if (c.clicks().any())
              throw new ScreenException(
                  source, line, "A <button> that shows a view cannot also have click handlers");
          }
        }
        case Gauge gauge -> requireValue(source, element, "a <gauge>", true);
        case Meter meter -> requireValue(source, element, "a <meter>", true);
        case Progress progress -> requireValue(source, element, "a <progress>", true);
        case Checkbox checkbox -> requireValue(source, element, "a <checkbox>", false);
        case RadioGroup group -> requireValue(source, element, "a <radio-group>", false);
        case SideConfig config -> {
          for (var group : config.groups())
            if (!ids.containsKey(group))
              throw new ScreenException(
                  source, line, "side-config group names no element: " + group);
        }
        case Popout popout -> {
          if (popout.kind() == PopoutKind.INFO && popout.text().isEmpty())
            throw new ScreenException(source, line, "An info <popout> needs text");
          if (popout.kind() != PopoutKind.INFO && c.value().isEmpty())
            throw new ScreenException(
                source,
                line,
                "A "
                    + popout.kind().name().toLowerCase()
                    + " <popout> needs a value getter reporting it");
        }
        case Spacer spacer -> {}
        default -> {}
      }
    }
    checkSpacers(screen);
    for (var element : screen.body()) checkFill(screen.source(), element, true);
    for (var element : screen.tabs()) checkFill(screen.source(), element, true);
  }

  /**
   * A {@code fill} length matches the box around it across, so a vertical gauge or meter must sit
   * directly in an {@code hbox}, and a horizontal one in a vertical stack (a {@code vbox}, group,
   * view, side tab, or the screen itself).
   *
   * @param verticalParent whether the element's parent stacks its children vertically
   */
  private static void checkFill(String source, Element element, boolean verticalParent) {
    Orientation orientation = null;
    if (element instanceof Gauge gauge && gauge.fills()) orientation = gauge.orientation();
    if (element instanceof Meter meter && meter.fills()) orientation = meter.orientation();
    if (orientation == Orientation.VERTICAL && verticalParent)
      throw new ScreenException(
          source,
          element.common().line(),
          "A vertical length=\"fill\" matches the height of its row, so it belongs directly in"
              + " an <hbox>");
    if (orientation == Orientation.HORIZONTAL && !verticalParent)
      throw new ScreenException(
          source,
          element.common().line(),
          "A horizontal length=\"fill\" matches the width of its column, so it belongs directly"
              + " in a <vbox>, <group>, <view> or <side-tab>");
    boolean vertical = !(element instanceof Box box && !box.vertical());
    for (var child : element.children()) checkFill(source, child, vertical);
  }

  private static void requireValue(String source, Element element, String what, boolean needsMax) {
    var c = element.common();
    if (c.value().isEmpty())
      throw new ScreenException(source, c.line(), what + " needs a value getter");
    if (needsMax && c.max().isEmpty())
      throw new ScreenException(source, c.line(), what + " needs a max getter");
  }

  private static void checkSpacers(Screen screen) {
    // A spacer only has room to fill inside a box.
    for (var element : screen.elements())
      if (!(element instanceof Box))
        for (var child : element.children())
          if (child instanceof Spacer)
            throw new ScreenException(
                screen.source(),
                child.common().line(),
                "<spacer> belongs directly inside a vbox or hbox");
    for (var element : screen.body())
      if (element instanceof Spacer)
        throw new ScreenException(
            screen.source(),
            element.common().line(),
            "<spacer> belongs directly inside a vbox or hbox");
  }
}
