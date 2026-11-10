package zone.moddev.mc.lib.gui.model.elements;

import java.util.Objects;
import java.util.Optional;

import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.ElementSnapshot;
import zone.moddev.mc.lib.gui.model.Orientation;
import zone.moddev.mc.lib.gui.model.ResourceBinding;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;

/**
 * An energy UI component owning its storage binding, synchronized values and default tooltip.
 * Storage is sampled only on the server; client rendering uses the resulting snapshots.
 */
public class EnergyGaugeElement implements Element {
  private final Common common;
  private final Orientation orientation;
  private final int length;
  private final int color;

  private static final Text TOOLTIP =
      new Text("screen.mmdlib.energy", Optional.of("{value} / {max} FE"));

  public EnergyGaugeElement(Common common, Orientation orientation, int length, int color) {
    Objects.requireNonNull(common);
    Objects.requireNonNull(orientation);
    if (length != FILL && length < 8)
      throw new IllegalArgumentException("Energy gauge length must be at least 8");

    this.common = common;
    this.orientation = orientation;
    this.length = length;
    this.color = color;
  }

  public boolean fills() {
    return length == FILL;
  }

  @Override
  public void validate() {
    Element.super.validate();
    if (common.value().isPresent() || common.max().isPresent())
      throw new IllegalArgumentException(
          "An energy gauge owns its values; expose screenEnergy storage");
  }

  @Override
  public Optional<ResourceBinding> resourceBinding() {
    return Optional.of(new ResourceBinding("energy", 0));
  }

  @Override
  public Optional<ElementSnapshot> snapshot(ScreenHost host) {
    var storage = host.screenEnergy();
    if (storage == null) throw new IllegalArgumentException("Energy gauge requires energy storage");
    return Optional.of(new ElementSnapshot(storage.stored(), storage.capacity()));
  }

  @Override
  public Optional<Text> tooltipText() {
    return common.tooltip().or(() -> Optional.of(TOOLTIP));
  }

  public int[] measure(TextMeasure text) {
    return ElementLayouts.oriented(orientation, 18, fills() ? 8 : length);
  }

  public boolean stretchX() {
    return fills();
  }

  public boolean stretchY() {
    return fills();
  }

  public Optional<Orientation> fillOrientation() {
    return fills() ? Optional.of(orientation()) : Optional.empty();
  }

  public Common common() {
    return common;
  }

  public Orientation orientation() {
    return orientation;
  }

  public int length() {
    return length;
  }

  public int color() {
    return color;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    var other = (EnergyGaugeElement) object;
    return java.util.Objects.equals(common, other.common)
        && java.util.Objects.equals(orientation, other.orientation)
        && java.util.Objects.equals(length, other.length)
        && java.util.Objects.equals(color, other.color);
  }

  @Override
  public int hashCode() {
    int hash = 0;
    hash = 31 * hash + java.util.Objects.hashCode(common);
    hash = 31 * hash + java.util.Objects.hashCode(orientation);
    hash = 31 * hash + java.util.Objects.hashCode(length);
    hash = 31 * hash + java.util.Objects.hashCode(color);
    return hash;
  }

  @Override
  public String toString() {
    return "EnergyGaugeElement[common="
        + common
        + ", orientation="
        + orientation
        + ", length="
        + length
        + ", color="
        + color
        + "]";
  }
}
