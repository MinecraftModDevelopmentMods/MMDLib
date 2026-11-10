package zone.moddev.mc.lib.gui.model.elements;

import static zone.moddev.mc.lib.gui.model.ValueContract.NUMBERS;

import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.ProgressDirection;
import zone.moddev.mc.lib.gui.model.ValueContract;
import zone.moddev.mc.lib.gui.value.ValueType;

/**
 * {@code progress}: a fill-in progress icon.
 *
 * <p>{@code icon}: the icon sprite; the default arrow when empty {@code direction}: which way it
 * fills
 */
public class ProgressElement implements Element {
  private final Common common;
  private final Optional<ResourceLocation> icon;
  private final ProgressDirection direction;

  public ProgressElement(
      Common common, Optional<ResourceLocation> icon, ProgressDirection direction) {
    this.common = common;
    this.icon = icon;
    this.direction = direction;
  }

  /** The processor arrow is a preset of the reusable progress sprite. */
  public ProgressSpriteElement spriteElement() {
    boolean vertical = direction == ProgressDirection.UP || direction == ProgressDirection.DOWN;
    return new ProgressSpriteElement(
        common,
        icon.orElse(ResourceLocation.parse("mmdlib:progress_arrow")),
        icon.orElse(ResourceLocation.parse("mmdlib:progress_arrow_filled")),
        vertical ? 16 : 24,
        vertical ? 24 : 16,
        direction,
        0xffffffff,
        icon.isPresent() ? 0xff404040 : 0xffffffff,
        icon.isEmpty());
  }

  public int[] measure(TextMeasure text) {
    return spriteElement().measure(text);
  }

  public Set<ValueType<?>> valueKinds(WidgetTypes widgets) {
    return NUMBERS;
  }

  public void validate() {
    Element.super.validate();
    ValueContract.require(this, "a <progress>", true);
  }

  public Common common() {
    return common;
  }

  public Optional<ResourceLocation> icon() {
    return icon;
  }

  public ProgressDirection direction() {
    return direction;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    var other = (ProgressElement) object;
    return java.util.Objects.equals(common, other.common)
        && java.util.Objects.equals(icon, other.icon)
        && java.util.Objects.equals(direction, other.direction);
  }

  @Override
  public int hashCode() {
    int hash = 0;
    hash = 31 * hash + java.util.Objects.hashCode(common);
    hash = 31 * hash + java.util.Objects.hashCode(icon);
    hash = 31 * hash + java.util.Objects.hashCode(direction);
    return hash;
  }

  @Override
  public String toString() {
    return "ProgressElement[common=" + common + ", icon=" + icon + ", direction=" + direction + "]";
  }
}
