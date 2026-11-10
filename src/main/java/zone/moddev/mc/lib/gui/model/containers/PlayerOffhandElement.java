package zone.moddev.mc.lib.gui.model.containers;

import java.util.List;

import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.model.Common;

/** Independently placeable player offhand slots. */
public class PlayerOffhandElement extends PlayerSlotsElement {
  public PlayerOffhandElement(Common common) {
    this(common, zone.moddev.mc.lib.gui.model.elements.PlayerSlotElement::player);
  }

  /** Use custom slot children while retaining this section's standard layout. */
  public PlayerOffhandElement(
      Common common,
      java.util.function.IntFunction<? extends zone.moddev.mc.lib.gui.model.elements.SlotElement>
          factory) {
    super(common, List.of(40), factory);
  }

  public Rect defaultBounds(Rect inventory) {
    return new Rect(inventory.x() + 166, inventory.y() + 76, 18, 18);
  }

  public String part() {
    return "offhand";
  }

  public int[] measure(TextMeasure text) {
    return new int[] {18, 18};
  }

  protected Rect position(int index) {
    return new Rect(0, 0, 18, 18);
  }
}
