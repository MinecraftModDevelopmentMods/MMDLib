package zone.moddev.mc.lib.gui.load;

import java.util.*;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.load.ScreenReader.Node;
import zone.moddev.mc.lib.gui.model.*;

/** Source-aware factory context. Children are always constructed through the same tag registry. */
public final class ScreenXml {
  private static final class State {
    final ResourceLocation id;
    final WidgetTypes widgets;
    final ScreenTags tags;
    final Map<Common, String> origins = new IdentityHashMap<>();
    final Map<Node, Common> common = new IdentityHashMap<>();
    final Map<Node, Object> parsed = new IdentityHashMap<>();

    State(ResourceLocation id, WidgetTypes widgets, ScreenTags tags) {
      this.id = id;
      this.widgets = widgets;
      this.tags = tags;
    }
  }

  private final State state;
  private final Node node;

  ScreenXml(ResourceLocation id, Node node, WidgetTypes widgets, ScreenTags tags) {
    this(new State(id, widgets, tags), node);
  }

  private ScreenXml(State state, Node node) {
    this.state = state;
    this.node = node;
  }

  public Node node() {
    return node;
  }

  public ResourceLocation screenId() {
    return state.id;
  }

  public WidgetTypes widgets() {
    return state.widgets;
  }

  public ScreenTags tags() {
    return state.tags;
  }

  public Map<Common, String> origins() {
    return Collections.unmodifiableMap(new IdentityHashMap<>(state.origins));
  }

  public Common common() {
    return state.common.computeIfAbsent(node, this::common);
  }

  public List<Element> children() {
    return children(Element.class);
  }

  public <T> List<T> children(Class<T> type) {
    return node.children().stream().map(child -> read(child, type)).toList();
  }

  public <T> T read(Node child, Class<T> type) {
    try {
      var parsed = state.parsed.get(child);
      if (parsed == null) {
        parsed = state.tags.require(child).create(new ScreenXml(state, child));
        state.parsed.put(child, parsed);
      }
      if (!type.isInstance(parsed))
        throw new IllegalArgumentException(
            "Tag produces "
                + parsed.getClass().getSimpleName()
                + "; expected "
                + type.getSimpleName());
      return type.cast(parsed);
    } catch (ScreenException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ScreenException(
          child.source(), child.line(), "<" + child.tag() + ">: " + failure.getMessage(), failure);
    }
  }

  public String attribute(String name) {
    var value = node.attributes().get(name);
    if (value == null) throw error("Missing attribute: " + name);
    return value;
  }

  public Optional<String> optional(String name) {
    return Optional.ofNullable(node.attributes().get(name));
  }

  public Optional<Text> text(String name) {
    return text(node, name);
  }

  public List<String> words(String name) {
    return words(node, name);
  }

  public Optional<ValueRef> value(String name) {
    return value(node, name);
  }

  public Optional<ResourceLocation> id(String name) {
    return id(node, name);
  }

  public Optional<Integer> color(String name) {
    return color(node, name);
  }

  public int size(String name, int minimum) {
    return size(node, name, minimum);
  }

  public int length(int minimum) {
    return length(node, minimum);
  }

  public int integer(String name) {
    return integer(node, name);
  }

  public boolean flag(String name) {
    return Set.of("true", "1").contains(attribute(name));
  }

  public <E extends Enum<E>> E enumValue(Class<E> type, String name) {
    return enumValue(type, node, name);
  }

  public ScreenException error(String message) {
    return error(node, message);
  }

  private Optional<Interaction> interaction(Node node) {
    var type = id(node, "interaction");
    var target = node.attributes().get("interaction-target");
    if (type.isEmpty()) {
      if (target != null) throw error(node, "interaction-target needs interaction");
      return Optional.empty();
    }
    return Optional.of(new Interaction(type.get(), target == null ? "" : target));
  }

  private Common common(Node node) {
    var a = node.attributes();
    var recipes = a.get("jei-recipes");
    var common =
        new Common(
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
            node.line(),
            interaction(node));
    state.origins.put(common, node.source());
    return common;
  }

  private Optional<Text> text(Node node, String name) {
    var key = node.attributes().get(name);
    var fallback = Optional.ofNullable(node.attributes().get(name + "-fallback"));
    if (key == null) {
      if (fallback.isPresent())
        throw error(node, name + "-fallback needs " + name + " (a translation key)");
      return Optional.empty();
    }
    return Optional.of(new Text(translationKey(key), fallback));
  }

  public String translationKey(String key) {
    return key.replace("{machineId}", state.id.toString());
  }

  private List<String> words(Node node, String name) {
    String value = node.attributes().getOrDefault(name, "").trim();
    return value.isEmpty() ? List.of() : List.of(value.split("\\s+"));
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
   * A size attribute in GUI pixels: a plain number is pixels, an {@code s} suffix counts slots (18
   * px each), rounded to the nearest pixel.
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
    return new ScreenException(node.source(), node.line(), message);
  }
}
