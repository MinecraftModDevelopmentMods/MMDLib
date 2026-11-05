package zone.moddev.mc.lib.gui.layout;

import java.util.LinkedHashSet;
import java.util.List;

import zone.moddev.mc.lib.api.automation.ResourceGroup;
import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;

/** Screen reading order, independent of persistent group IDs and real menu slot indices. */
public final class InventoryColorOrder {
  private InventoryColorOrder() {}

  public static List<Integer> of(Screen screen, List<ResourceGroup<?>> groups) {
    var order = new LinkedHashSet<Integer>();
    // Boxes expose their children in their displayed order (rows left-to-right, stacks top-down).
    // An inventory with several anchors appears once, at its first screen occurrence.
    for (var element : screen.elements()) {
      for (int i = 0; i < groups.size(); i++) {
        var group = groups.get(i);
        boolean anchor = element.common().id().filter(group.highlights()::contains).isPresent();
        if (anchor
            || (group.highlights().isEmpty()
                && element instanceof Element.Slot slot
                && group.type() == ResourceType.ITEMS
                && group.contains(slot.index()))) order.add(i);
      }
    }
    // Groups without a screen anchor remain reachable, in their declared order.
    for (int i = 0; i < groups.size(); i++) order.add(i);
    return List.copyOf(order);
  }
}
