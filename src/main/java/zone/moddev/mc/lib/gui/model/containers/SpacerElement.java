package zone.moddev.mc.lib.gui.model.containers;

import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;

/** {@code spacer}: takes the leftover room in its box. */
public class SpacerElement implements Element {
  private final Common common;

  public SpacerElement(Common common) {
    this.common = common;
  }

  public int[] measure(TextMeasure text) {
    return new int[] {0, 0};
  }

  public boolean spacer() {
    return true;
  }

  public Common common() {
    return common;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    var other = (SpacerElement) object;
    return java.util.Objects.equals(common, other.common);
  }

  @Override
  public int hashCode() {
    int hash = 0;
    hash = 31 * hash + java.util.Objects.hashCode(common);
    return hash;
  }

  @Override
  public String toString() {
    return "SpacerElement[common=" + common + "]";
  }
}
