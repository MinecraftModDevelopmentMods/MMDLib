package zone.moddev.mc.lib.gui.model.containers;

import java.util.List;

import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.model.Common;

/** Independently placeable player armour slots. */
public class PlayerArmourElement extends PlayerSlotsElement {
  public PlayerArmourElement(Common common) {
    this(common, zone.moddev.mc.lib.gui.model.elements.PlayerSlotElement::player);
  }

  /** Use custom slot children while retaining this section's standard layout. */
  public PlayerArmourElement(
      Common common,
      java.util.function.IntFunction<? extends zone.moddev.mc.lib.gui.model.elements.SlotElement>
          factory) {
    super(common, List.of(39, 38, 37, 36), factory);
  }

  public Rect defaultBounds(Rect inventory) {
    return new Rect(inventory.x() + 166, inventory.y(), 18, 72);
  }

  public String part() {
    return "armour";
  }

  public int[] measure(TextMeasure text) {
    return new int[] {18, 72};
  }

  protected Rect position(int index) {
    return new Rect(0, (39 - index) * 18, 18, 18);
  }
}
