package zone.moddev.mc.lib.gui.model.containers;

import static zone.moddev.mc.lib.api.automation.AutomationInventory.COLUMNS;

import java.util.*;

import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.model.elements.*;

/** Addon and filter slot presentation for one independently backed automation group. */
public class AutomationInventoryElement extends InventoryElement {
  public AutomationInventoryElement(String inventory) {
    this(inventory, AutomationInventoryElement::defaultSlot);
  }

  public AutomationInventoryElement(
      String inventory, java.util.function.IntFunction<? extends SlotElement> factory) {
    super(Common.empty(), inventory, COLUMNS, 2, addonSlots(factory));
  }

  private static List<Element> addonSlots(
      java.util.function.IntFunction<? extends SlotElement> factory) {
    var children = new ArrayList<Element>();
    for (int i = 0; i < COLUMNS * 2; i++) {
      var slot = Objects.requireNonNull(factory.apply(i));
      if (slot.index() != i || slot.ghost() || slot.playerInventory())
        throw new IllegalArgumentException(
            "Automation slots must retain real local storage indexes");
      children.add(slot);
    }
    return List.copyOf(children);
  }

  /** Default preset, reusable by slot factories that only customize some columns. */
  public static SlotElement defaultSlot(int i) {
    if (i < 0 || i >= COLUMNS * 2) throw new IllegalArgumentException("Invalid automation slot");
    var c = Common.empty();
    if (i >= COLUMNS)
      c =
          new Common(
              c.id(),
              c.value(),
              c.max(),
              c.tooltip(),
              c.tooltipShift(),
              c.clicks(),
              c.onHover(),
              c.hoverShows(),
              c.jeiRecipes(),
              2,
              2,
              0,
              c.interaction());
    return new AutomationSlotElement(
        c,
        i,
        new SlotElement.Appearance(
            SlotSize.NORMAL,
            Optional.of(0xffffff),
            Optional.empty(),
            net.minecraft.resources.ResourceLocation.parse("mmdlib:slot"),
            .42f,
            Optional.empty(),
            i < COLUMNS ? 16 : 12));
  }

  /**
   * Read-only group data; contents and labels are detached copies. No pending interaction state.
   */
  public interface Presentation {
    net.minecraft.network.chat.Component name();

    net.minecraft.world.item.ItemStack contents(int localSlot);

    boolean supportsFilter(int column);
  }

  /** Display policy shared by native activation and fragment rendering. */
  public boolean slotVisible(SlotElement slot, Presentation context) {
    return slot.index() < COLUMNS
        || context.supportsFilter(slot.index() - COLUMNS)
        || !context.contents(slot.index()).isEmpty();
  }

  /** Values exposed to this inventory's renderers and tooltip hooks. */
  public Object drawerValue(Element element, boolean maximum, Presentation context) {
    if (maximum) return null;
    return element instanceof SlotElement slot ? context.contents(slot.index()) : context.name();
  }

  /** Slot frame relative to the drawer body, shared by native assembly and rendering. */
  public zone.moddev.mc.lib.gui.layout.Rect drawerSlotBounds(int index) {
    return slotBounds(index).offset(26, 74);
  }

  public zone.moddev.mc.lib.gui.layout.Rect filterToggleBounds(int column) {
    var bounds = drawerSlotBounds(COLUMNS + column);
    return new zone.moddev.mc.lib.gui.layout.Rect(
        bounds.x(), bounds.bottom() + 2, bounds.width(), 7);
  }

  @Override
  public int[] measure(zone.moddev.mc.lib.gui.layout.TextMeasure text) {
    int width = 0, height = 0;
    for (var slot : slots()) {
      var bounds = drawerSlotBounds(slot.index());
      width = Math.max(width, bounds.right());
      height = Math.max(height, bounds.bottom());
    }
    return new int[] {width, height};
  }

  @Override
  public Map<Element, zone.moddev.mc.lib.gui.layout.Rect> childLayout(
      zone.moddev.mc.lib.gui.layout.Rect bounds, zone.moddev.mc.lib.gui.layout.TextMeasure text) {
    var result = new IdentityHashMap<Element, zone.moddev.mc.lib.gui.layout.Rect>();
    for (var slot : slots()) result.put(slot, drawerSlotBounds(slot.index()));
    return result;
  }

  @Override
  public void appendChildTooltip(
      TooltipContext context, List<net.minecraft.network.chat.Component> rows) {
    super.appendChildTooltip(context, rows);
    if (context.target() instanceof SlotElement slot
        && slot.index() < COLUMNS
        && context.value(slot, false) instanceof net.minecraft.world.item.ItemStack stack
        && stack.isEmpty()
        && context.value(this, false) instanceof net.minecraft.network.chat.Component name)
      rows.add(
          net.minecraft.network.chat.Component.translatable("screen.mmdlib.automation.addon")
              .append(": ")
              .append(name.copy()));
  }

  public int contentHeight(boolean filterSlot, boolean configuredFilter) {
    int bottom = 0;
    for (int c = 0; c < COLUMNS; c++)
      bottom =
          Math.max(
              bottom,
              configuredFilter
                  ? filterToggleBounds(c).bottom()
                  : drawerSlotBounds(filterSlot ? COLUMNS + c : c).bottom());
    return bottom + 4;
  }
}
