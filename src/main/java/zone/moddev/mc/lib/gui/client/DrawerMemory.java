package zone.moddev.mc.lib.gui.client;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.runtime.ContainerDrawerMenu;

/** Connection-scoped drawer preferences shared by every MMDLib machine screen. */
final class DrawerMemory {
  private record Selection(String kind, String key, int offset) {}

  private static Object connection;
  private static final Map<Element.Edge, Selection> remembered = new EnumMap<>(Element.Edge.class);
  private final Map<Element.Edge, Selection> restoring = new EnumMap<>(Element.Edge.class);
  private int ticks;

  DrawerMemory() {
    Object current = Minecraft.getInstance().getConnection();
    if (connection != current) {
      connection = current;
      remembered.clear();
    }
    restoring.putAll(remembered);
  }

  void userInput() {
    restoring.clear();
  }

  void restore(MachineScreen screen, java.util.Set<Element.Popout> open) {
    if (++ticks > 100) {
      restoring.clear();
      return;
    }
    var menu = screen.getMenu();
    for (var edge : Element.Edge.values()) {
      var selection = restoring.get(edge);
      if (selection == null) continue;
      if (selection.kind().equals("backpack") || selection.kind().equals("curios")) {
        boolean equipment = selection.kind().equals("curios");
        var drawer = equipment ? menu.curiosDrawer : menu.containerDrawer;
        if (!menu.bound.screen().playerInventory()) {
          restoring.remove(edge);
          continue;
        }
        var state = drawer.state();
        if (state.keys().isEmpty() || drawer.pending()) continue;
        int index = state.keys().indexOf(selection.key());
        if (index < 0) {
          restoring.remove(edge);
          continue;
        }
        if (!state.open()) {
          drawer
              .request(ContainerDrawerMenu.TOGGLE, 0, state.pageSize())
              .ifPresent(PacketDistributor::sendToServer);
        } else if (state.selected() != index) {
          drawer
              .request(ContainerDrawerMenu.SELECT, index, state.pageSize())
              .ifPresent(PacketDistributor::sendToServer);
        } else {
          int offset = Math.min(selection.offset(), Math.max(0, state.sizes().get(index) - 1));
          offset = offset / state.pageSize() * state.pageSize();
          if (state.offset() != offset)
            drawer
                .request(ContainerDrawerMenu.PAGE, offset, state.pageSize())
                .ifPresent(PacketDistributor::sendToServer);
          restoring.remove(edge);
          screen.closeLeftDrawers();
        }
      } else if (selection.kind().equals("automation")) {
        var automation = menu.automation;
        if (automation == null) {
          restoring.remove(edge);
          continue;
        }
        if (automation.pending()) continue;
        int group = automation.inventory.index(selection.key());
        if (group < 0) group = 0;
        automation.send("select", group, -1, "", 0, "");
        restoring.remove(edge);
      } else {
        menu.bound.screen().tabs().stream()
            .filter(
                e ->
                    e instanceof Element.Popout p
                        && p.edge() == edge
                        && p.kind().name().equals(selection.kind()))
            .map(Element.Popout.class::cast)
            .findFirst()
            .ifPresent(open::add);
        restoring.remove(edge);
      }
    }
  }

  void containerIntent(
      boolean equipment,
      zone.moddev.mc.lib.gui.runtime.ContainerDrawerNetwork.State state,
      int kind,
      int argument) {
    if (state.keys().isEmpty()) return;
    if (kind == ContainerDrawerMenu.TOGGLE && state.open()) remembered.remove(Element.Edge.LEFT);
    else if (kind == ContainerDrawerMenu.TOGGLE
        || kind == ContainerDrawerMenu.SELECT
        || kind == ContainerDrawerMenu.PAGE) {
      int index = kind == ContainerDrawerMenu.SELECT ? argument : state.selected();
      remembered.put(
          Element.Edge.LEFT,
          new Selection(
              equipment ? "curios" : "backpack",
              state.keys().get(index),
              kind == ContainerDrawerMenu.PAGE ? argument : state.offset()));
    }
  }

  void automationIntent(String kind, String group) {
    if (kind.equals("close")) remembered.remove(Element.Edge.RIGHT);
    else if (kind.equals("select"))
      remembered.put(Element.Edge.RIGHT, new Selection("automation", group, 0));
  }

  void popoutIntent(Element.Popout popout, boolean open) {
    if (open) remembered.put(popout.edge(), new Selection(popout.kind().name(), "", 0));
    else remembered.remove(popout.edge());
  }

  void capture(MachineScreen screen, java.util.Set<Element.Popout> open) {
    var menu = screen.getMenu();
    for (var edge : Element.Edge.values()) {
      // Do not erase a preference while waiting for a fresh menu's equipment snapshot.
      if (restoring.containsKey(edge)
          || edge == Element.Edge.LEFT
              && (menu.containerDrawer.pending() || menu.curiosDrawer.pending())
          || edge == Element.Edge.RIGHT && menu.automation != null && menu.automation.pending())
        continue;
      Selection selection = null;
      if (edge == Element.Edge.LEFT) {
        for (var drawer : java.util.List.of(menu.containerDrawer, menu.curiosDrawer)) {
          var state = drawer.state();
          if (state.open() && !state.keys().isEmpty())
            selection =
                new Selection(
                    state.equipment() ? "curios" : "backpack",
                    state.keys().get(state.selected()),
                    state.offset());
        }
      } else if (menu.automation != null && menu.automation.open()) {
        var a = menu.automation;
        selection = new Selection("automation", a.inventory.group(a.selected()).id(), 0);
      }
      if (selection == null)
        for (var popout : open)
          if (popout.edge() == edge) selection = new Selection(popout.kind().name(), "", 0);
      if (selection != null) remembered.put(edge, selection);
      else {
        var previous = remembered.get(edge);
        boolean unavailable =
            previous != null
                && (previous.kind().equals("backpack")
                        && menu.containerDrawer.state().keys().isEmpty()
                    || previous.kind().equals("curios")
                        && menu.curiosDrawer.state().keys().isEmpty()
                    || previous.kind().equals("automation") && menu.automation == null);
        if (!unavailable) remembered.remove(edge);
      }
    }
  }
}
