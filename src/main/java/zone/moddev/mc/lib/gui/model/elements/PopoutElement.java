package zone.moddev.mc.lib.gui.model.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Edge;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.PopoutKind;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.TextMarkup;
import zone.moddev.mc.lib.gui.value.ValueType;
import zone.moddev.mc.lib.gui.value.ValueTypes;

/**
 * {@code popout}: an information pop-out on an edge tab. An {@code info} pop-out shows {@code
 * text}; {@code warning} and {@code error} pop-outs show what their value getter reports.
 *
 * <p>{@code kind}: what it reports {@code icon}: the tab icon; the kind's default when empty {@code
 * edge}: the panel edge {@code text}: the fixed text of an info pop-out
 */
public class PopoutElement implements Element {
  private final Common common;
  private final PopoutKind kind;
  private final Optional<ResourceLocation> icon;
  private final Edge edge;
  private final Optional<Text> text;

  public PopoutElement(
      Common common,
      PopoutKind kind,
      Optional<ResourceLocation> icon,
      Edge edge,
      Optional<Text> text) {
    this.common = common;
    this.kind = kind;
    this.icon = icon;
    this.edge = edge;
    this.text = text;
  }

  /** Stable preference identity shared by the drawer provider and client instance. */
  public String drawerId(zone.moddev.mc.lib.gui.model.Screen screen) {
    if (common.id().isPresent()) return "popout:id:" + common.id().get();
    int ordinal = 0;
    for (var tab : screen.tabs()) {
      if (tab == this) break;
      if (tab instanceof PopoutElement other
          && other.edge() == edge
          && other.kind() == kind
          && other.common().id().isEmpty()) ordinal++;
    }
    return "popout:kind:" + kind.name() + ":" + ordinal;
  }

  /** The tab icon: the given one, or {@code mmdlib:icon_<kind>}. */
  public ResourceLocation iconOrDefault() {
    return icon.orElse(
        ResourceLocation.fromNamespaceAndPath(
            "mmdlib", "icon_" + kind.name().toLowerCase(java.util.Locale.ROOT)));
  }

  /** RGB tab and body colour. Subclasses may supply their own appearance. */
  public int colour() {
    return switch (kind()) {
      case INFO -> 0xf1d462;
      case WARNING -> 0xe7ac50;
      case ERROR -> 0xd85c59;
    };
  }

  /**
   * Unwrapped content, in display order. Empty components are blank rows. The resolver supplies
   * translated text; fixed text supports screen emphasis, while live components retain their
   * styles. Subclasses can interpret other synchronized values without changing the drawer or
   * renderer.
   */
  public List<Component> content(Object value, Function<Text, Component> resolveText) {
    var result = new ArrayList<Component>();
    text().ifPresent(text -> result.add(TextMarkup.markup(resolveText.apply(text).getString())));
    if (!result.isEmpty()
        && (value instanceof Component || value instanceof List<?> list && !list.isEmpty()))
      result.add(Component.empty());
    if (value instanceof Component component) result.add(component.copy());
    else if (value instanceof List<?> list)
      for (var item : list)
        if (item instanceof Component component) {
          result.add(component.copy());
          result.add(Component.empty());
        }
    return List.copyOf(result);
  }

  public int[] measure(TextMeasure text) {
    return new int[] {22, 24};
  }

  public Set<ValueType<?>> valueKinds(WidgetTypes widgets) {
    return Set.of(ValueTypes.TEXT, ValueTypes.TEXT_LIST);
  }

  public void validate() {
    Element.super.validate();
    if (kind == PopoutKind.INFO && text.isEmpty())
      throw new IllegalArgumentException("An info <popout> needs text");
    if (kind != PopoutKind.INFO && common.value().isEmpty())
      throw new IllegalArgumentException(
          "A "
              + kind.name().toLowerCase(java.util.Locale.ROOT)
              + " <popout> needs a value getter reporting it");
  }

  public Common common() {
    return common;
  }

  public PopoutKind kind() {
    return kind;
  }

  public Optional<ResourceLocation> icon() {
    return icon;
  }

  public Edge edge() {
    return edge;
  }

  public Optional<Text> text() {
    return text;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    var other = (PopoutElement) object;
    return java.util.Objects.equals(common, other.common)
        && java.util.Objects.equals(kind, other.kind)
        && java.util.Objects.equals(icon, other.icon)
        && java.util.Objects.equals(edge, other.edge)
        && java.util.Objects.equals(text, other.text);
  }

  @Override
  public int hashCode() {
    int hash = 0;
    hash = 31 * hash + java.util.Objects.hashCode(common);
    hash = 31 * hash + java.util.Objects.hashCode(kind);
    hash = 31 * hash + java.util.Objects.hashCode(icon);
    hash = 31 * hash + java.util.Objects.hashCode(edge);
    hash = 31 * hash + java.util.Objects.hashCode(text);
    return hash;
  }

  @Override
  public String toString() {
    return "PopoutElement[common="
        + common
        + ", kind="
        + kind
        + ", icon="
        + icon
        + ", edge="
        + edge
        + ", text="
        + text
        + "]";
  }
}
