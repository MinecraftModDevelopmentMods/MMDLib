package zone.moddev.mc.companion.test;

import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.client.*;
import zone.moddev.mc.lib.gui.drawer.BuiltinDrawers;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.TooltipContext;
import zone.moddev.mc.lib.gui.model.containers.ContainerInventoryElement;
import zone.moddev.mc.lib.gui.model.elements.ContainerSlotElement;
import zone.moddev.mc.lib.gui.runtime.*;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;
import zone.moddev.mc.lib.gui.runtime.slot.MenuSlots;

/** Uses the real drawer factories and synchronized pages, without replacing their gesture code. */
final class ContainerCellClientSmoke {
  private static int mode, hitCalls, headings, labels;
  private static boolean inputProbe;
  private static int clicks, drags, releases, keys, keyUps, characters, scrolls, cancels, enters;
  private static boolean focused;
  private static int probeIndex = -1;
  private static final Set<String> checked = new HashSet<>();
  private static final EnumSet<ElementRenderers.Pass> passes =
      EnumSet.noneOf(ElementRenderers.Pass.class);

  private static class Cell extends ContainerSlotElement {
    Cell(int index, ContainerInventoryElement.Page page) {
      super(index, page.description(index));
    }

    @Override
    public Rect cellHitBounds(Rect frame) {
      hitCalls++;
      return index() == probeIndex
          ? new Rect(frame.x() - 4, frame.y() + 4, 6, 6)
          : super.cellHitBounds(frame);
    }

    public List<Component> nativeTooltip(ItemStack stack, List<Component> vanilla) {
      stack.setCount(1);
      return mode == 0
          ? vanilla
          : mode == 1 ? List.of(Component.literal("native cell replacement")) : List.of();
    }

    public void appendTooltip(TooltipContext c, List<Component> rows) {
      super.appendTooltip(c, rows);
      rows.add(Component.literal("cell row"));
    }
  }

  private static class Inventory extends ContainerInventoryElement {
    Inventory(Page page) {
      super(page, index -> new Cell(index, page));
    }

    public List<Component> headings() {
      headings++;
      return super.headings();
    }

    public Component cellLabel(zone.moddev.mc.lib.gui.model.elements.SlotElement slot) {
      labels++;
      return super.cellLabel(slot);
    }

    public void appendChildTooltip(TooltipContext c, List<Component> rows) {
      rows.add(Component.literal("parent cell row"));
    }
  }

  static void install() {
    for (var type : List.of(BuiltinDrawers.BACKPACK, BuiltinDrawers.CURIOS))
      DrawerFactories.GLOBAL.replace(
          type,
          (screen, definition) ->
              new ContainerDrawer(screen, definition, type.equals(BuiltinDrawers.CURIOS)) {
                protected ContainerInventoryElement createInventory(
                    ContainerInventoryElement.Page page) {
                  return new Inventory(page);
                }
              });
    var base = ElementRenderers.GLOBAL.require(ContainerSlotElement.class);
    for (var pass : ElementRenderers.Pass.values())
      ElementRenderers.GLOBAL.register(
          pass,
          Cell.class,
          ElementRenderers.Clip.ELEMENT,
          (cell, c) -> {
            passes.add(pass);
            if (pass == ElementRenderers.Pass.BACKGROUND) {
              int before = hitCalls;
              base.accept(c);
              if (cell.available())
                require(hitCalls > before, "Default hover renderer consults overridable cell hits");
            }
            if (pass == ElementRenderers.Pass.TOP)
              c.graphics()
                  .renderOutline(
                      c.bounds().x(),
                      c.bounds().y(),
                      c.bounds().width(),
                      c.bounds().height(),
                      0xff55aacc);
          });
    ElementBehaviors.GLOBAL.register(
        Cell.class,
        (cell, access) ->
            new SlotBehavior(cell, access) {
              private boolean probing() {
                return inputProbe && cell.index() == probeIndex;
              }

              public boolean focusable() {
                return probing();
              }

              public void setFocused(boolean value) {
                focused = value;
              }

              public Click mouseClicked(double x, double y, int button) {
                if (!probing()) return super.mouseClicked(x, y, button);
                clicks++;
                return Click.CAPTURE;
              }

              public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
                drags++;
                return true;
              }

              public boolean mouseReleased(double x, double y, int button) {
                releases++;
                return true;
              }

              public boolean keyPressed(int key, int scan, int modifiers) {
                keys++;
                return true;
              }

              public boolean keyReleased(int key, int scan, int modifiers) {
                keyUps++;
                return true;
              }

              public boolean charTyped(char value, int modifiers) {
                characters++;
                return true;
              }

              public boolean mouseScrolled(double x, double y, double dx, double dy) {
                if (!probing()) return false;
                scrolls++;
                return true;
              }

              public void cancelInteraction() {
                cancels++;
              }

              public void hoverChanged(boolean value) {
                if (probing() && value) enters++;
              }
            });
    ElementBehaviors.GLOBAL.register(
        Inventory.class,
        (element, access) ->
            new DefaultElementBehavior(access) {
              public void appendChildTooltip(TooltipContext c, List<Component> rows) {
                rows.add(Component.literal("parent behavior row"));
              }
            });
  }

  static void rendered(MachineScreen screen, net.minecraft.client.gui.GuiGraphics graphics) {
    for (boolean equipment : new boolean[] {false, true}) {
      String id = equipment ? "curios" : "backpack";
      if (checked.contains(id)) continue;
      var model =
          screen
              .getMenu()
              .components()
              .require(equipment ? BuiltinMenuComponents.CURIOS : BuiltinMenuComponents.BACKPACK);
      var state = model.state();
      if (!state.open() || state.contents().isEmpty() || model.pending()) continue;
      var drawer =
          (ContainerDrawer)
              screen.drawers().entries().stream()
                  .filter(d -> d.id().equals(id))
                  .findFirst()
                  .orElseThrow();
      var layout = drawer.contentLayout();
      if (state.pageSize() != layout.pageSize() || passes.size() != 4) continue;
      int occupied = -1, empty = -1;
      for (int i = 0; i < state.contents().size(); i++) {
        if (state.contents().get(i).isEmpty()) empty = i;
        else occupied = i;
      }
      if (occupied < 0 || empty < 0) continue;
      var cell = layout.cell(occupied);
      var before = state.contents().get(occupied).copy();
      try {
        for (mode = 0; mode < 3; mode++) {
          var rows =
              drawer.cellTooltip(cell.x() + 8, cell.y() + 8).stream()
                  .map(Component::getString)
                  .toList();
          require(
              rows.contains("cell row")
                  && rows.contains("parent cell row")
                  && rows.contains("parent behavior row"),
              "Parent rows survive every native tooltip mode");
          require(
              mode != 1 || rows.getFirst().equals("native cell replacement"),
              "Native replacement used");
          require(
              mode != 2 || !rows.contains(before.getHoverName().getString()),
              "Native rows suppressed");
          require(
              ItemStack.matches(before, state.contents().get(occupied)),
              "Tooltip mutation cannot alter snapshot");
          if (equipment)
            for (var row : state.descriptions().get(occupied).tooltip())
              require(
                  rows.contains(row.getString()), "Provider tooltip retained on occupied cells");
        }
      } finally {
        mode = 0;
      }
      var blank = layout.cell(empty);
      var rows =
          drawer.cellTooltip(blank.x() + 8, blank.y() + 8).stream()
              .map(Component::getString)
              .toList();
      require(
          rows.contains("parent cell row") && rows.contains("cell row"),
          "Empty cells compose rows");
      var first = layout.cell(0);
      probeIndex = state.offset();
      try {
        require(
            drawer.cellTooltip(first.x() - 2, first.y() + 6).stream()
                .anyMatch(row -> row.getString().equals("parent cell row")),
            "Expanded hit outside frame composes parent rows");
        require(
            drawer.cellTooltip(first.x() + 8, first.y() + 8).isEmpty(),
            "Frame outside shrunken hit has no cell tooltip");
      } finally {
        probeIndex = -1;
      }
      input(screen, drawer, model, first);
      presentation();
      require(headings > 0 && labels > 0, "Renderer consults inventory presentation hooks");
      nativeHits();
      checked.add(id);
      var mc = Minecraft.getInstance();
      graphics.flush();
      Screenshot.grab(
          mc.gameDirectory, id + "-cell-elements.png", mc.getMainRenderTarget(), message -> {});
    }
  }

  private static void input(
      MachineScreen screen, ContainerDrawer drawer, ContainerDrawerMenu model, Rect frame) {
    var state = model.state();
    var carried = screen.getMenu().getCarried().copy();
    probeIndex = state.offset();
    inputProbe = true;
    clicks = drags = releases = keys = keyUps = characters = scrolls = cancels = enters = 0;
    double x = frame.x() - 2, y = frame.y() + 6;
    try {
      screen.mouseMoved(frame.x() + 8, frame.y() + 8);
      screen.mouseMoved(x, y);
      require(enters == 1, "Generic hover respects expanded cell bounds");
      require(
          screen.mouseClicked(x, y, 0) && clicks == 1 && focused,
          "Generic cell click captures and focuses outside its frame");
      require(
          screen.mouseDragged(-10, -10, 0, -1, -1) && drags == 1,
          "Capture receives drag outside drawer");
      require(
          screen.mouseReleased(-10, -10, 0) && releases == 1,
          "Capture receives release outside drawer");
      require(
          screen.keyPressed(65, 0, 0)
              && screen.keyReleased(65, 0, 0)
              && screen.charTyped('a', 0)
              && keys == 1
              && keyUps == 1
              && characters == 1,
          "Focused cells receive keyboard, release and text input");
      require(drawer.scroll(x, y, 1) && scrolls == 1, "Cell scrolling precedes page fallback");
      require(
          !model.pending() && ItemStack.matches(carried, screen.getMenu().getCarried()),
          "Consumed generic input does not trigger item gestures");
      screen.mouseClicked(x, y, 0);
      var request =
          model
              .request(ContainerDrawerMenu.PICKUP, state.offset() * 2, state.pageSize())
              .orElseThrow();
      try {
        screen.mouseDragged(x, y, 0, 1, 1);
        screen.keyPressed(65, 0, 0);
        screen.keyReleased(65, 0, 0);
        screen.charTyped('b', 0);
        drawer.scroll(x, y, 1);
        require(
            cancels > 0
                && !focused
                && drags == 1
                && keys == 1
                && keyUps == 1
                && characters == 1
                && scrolls == 1,
            "Pending lock cancels capture and blocks callbacks");
      } finally {
        model.receive(
            new ContainerDrawerNetwork.State(
                state.menu(),
                state.equipment(),
                state.session(),
                state.revision(),
                request.request(),
                state.open(),
                state.selected(),
                state.offset(),
                state.pageSize(),
                state.icons(),
                state.keys(),
                state.sizes(),
                state.contents(),
                state.descriptions(),
                state.presentations()));
      }
      require(
          screen.mouseReleased(x, y, 0) && releases == 1,
          "Stale release after pending cancellation is consumed without committing");
      screen.mouseClicked(x, y, 0);
      int cancelled = cancels;
      drawer.resized();
      require(
          cancels > cancelled && screen.mouseReleased(x, y, 0) && releases == 1,
          "Resize cancels generic capture and consumes stale release");
    } finally {
      drawer.resized();
      inputProbe = false;
      probeIndex = -1;
    }
  }

  private static void presentation() {
    var icon = net.minecraft.resources.ResourceLocation.parse("test:override");
    var info =
        new EquippedContainers.SlotInfo(
            "override",
            0,
            false,
            false,
            icon,
            Component.literal("overridden label"),
            List.of(Component.literal("overridden row")),
            Component.literal("overridden placeholder"));
    var cell =
        new ContainerSlotElement(0, Optional.empty()) {
          public Optional<EquippedContainers.SlotInfo> description() {
            return Optional.of(info);
          }
        };
    require(
        !cell.available()
            && cell.icon().orElseThrow().equals(icon)
            && cell.cellLabel().getString().equals("overridden label")
            && cell.placeholder().getString().equals("overridden placeholder"),
        "Overridden description controls default cell presentation");
  }

  private static void nativeHits() {
    var slots = new MenuSlots();
    boolean[] active = {true};
    var nativeSlot =
        new net.minecraft.world.inventory.Slot(
            new net.minecraft.world.SimpleContainer(1), 0, 20, 20) {
          public boolean isActive() {
            return active[0];
          }
        };
    var element =
        new ContainerSlotElement(0, Optional.empty()) {
          public Rect hitBounds(Rect original) {
            return new Rect(10, 22, 6, 6);
          }
        };
    slots.registrar(slot -> slot.index = 0).add(nativeSlot, element, MenuSlots.Role.PLAYER_STORAGE);
    var hits = new NativeSlotRenderer();
    require(
        hits.hit(slots, 9, 22) == nativeSlot && hits.hit(slots, 24, 24) == null,
        "Custom hits and tolerance replace fixed vanilla bounds");
    active[0] = false;
    require(
        hits.hit(slots, 9, 22) == null && hits.hit(slots, 9, 22, false) == nativeSlot,
        "Pending geometry stays blocked without enabling interaction");
  }

  static void verify() {
    if (BackpackFixture.installed())
      require(checked.contains("backpack"), "Backpack cell policies were checked");
    if (net.neoforged.fml.ModList.get().isLoaded("curios"))
      require(checked.contains("curios"), "Equipment cell policies were checked");
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
