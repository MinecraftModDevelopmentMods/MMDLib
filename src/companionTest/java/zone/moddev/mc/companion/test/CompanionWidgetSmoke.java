package zone.moddev.mc.companion.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

import net.neoforged.neoforge.network.PacketDistributor;

import zone.moddev.mc.companion.CompanionWidgets;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.model.Element;

/** Real client focus, text editing, captures, resize, navigation and acknowledged actions. */
final class CompanionWidgetSmoke {
  private static int step;
  private static zone.moddev.mc.lib.gui.layout.Rect compactPanel;
  private static java.util.List<net.minecraft.world.inventory.Slot> originalSlots;

  private CompanionWidgetSmoke() {}

  private static void click(MachineScreen s, double x, double y) {
    if (!s.mouseClicked(x, y, 0))
      throw new AssertionError("Widget click not handled at step " + step);
    s.mouseReleased(x, y, 0);
  }

  private static void type(MachineScreen s, String text) {
    s.keyPressed(269, 0, 0);
    for (int i = 0; i < 32; i++) s.keyPressed(259, 0, 0);
    for (char c : text.toCharArray()) {
      s.keyPressed(Character.toUpperCase(c), 0, 0);
      if (!s.charTyped(c, 0)) throw new AssertionError("Text field lost focus at step " + step);
    }
    if (Minecraft.getInstance().screen != s)
      throw new AssertionError("Inventory shortcut closed focused editor");
  }

  private static boolean nameIs(MachineScreen s, Element.Custom e, String name) {
    return s.getMenu().value(e, false) instanceof Component c && c.getString().equals(name);
  }

  static boolean run(MachineScreen s) {
    var mc = Minecraft.getInstance();
    var menu = s.getMenu();
    var name = (Element.Custom) menu.bound.screen().find("name_editor").orElseThrow();
    var slider = (Element.Custom) menu.bound.screen().find("reserve_slider").orElseThrow();
    var selector = (Element.Custom) menu.bound.screen().find("lane_selector").orElseThrow();
    var n = s.bounds(name);
    var r = s.bounds(slider);
    var l = s.bounds(selector);
    if (originalSlots != null) {
      for (int i = 0; i < menu.slots.size(); i++) {
        var slot = menu.slots.get(i);
        if (slot != originalSlots.get(i))
          throw new AssertionError("View change replaced menu slot");
        if (slot.container == mc.player.getInventory()) {
          var expected =
              zone.moddev.mc.lib.gui.layout.ScreenLayout.playerSlot(
                  s.layout.inventory().orElseThrow(), slot.getSlotIndex());
          if (slot.x != expected.x() + 1 || slot.y != expected.y() + 1)
            throw new AssertionError("Resized player slot does not match its background");
        }
      }
      for (var element : menu.elements) {
        if (element instanceof Element.Slot slotElement) {
          var slot = menu.slots.get(slotElement.index());
          var expected =
              zone.moddev.mc.lib.gui.layout.ScreenLayout.itemPosition(s.layout.bounds(element));
          if (slot.x != expected[0] || slot.y != expected[1])
            throw new AssertionError("Resized machine slot does not match its background");
        }
      }
      if (s.guiLeft() != (s.width - s.panelBounds().width()) / 2
          || s.guiTop() != (s.height - s.panelBounds().height()) / 2)
        throw new AssertionError("Resized panel is not centred");
    }
    switch (step) {
      case 0 -> {
        compactPanel = s.panelBounds();
        originalSlots = java.util.List.copyOf(menu.slots);
        Screenshot.grab(
            mc.gameDirectory, "widget-collapsed.png", mc.getMainRenderTarget(), message -> {});
        var open = s.bounds(menu.bound.screen().find("open_settings").orElseThrow());
        click(s, open.x() + 8, open.y() + 8);
      }
      case 1 -> {
        if (!s.visible(name) || !(menu.value(name, false) instanceof Component)) return false;
        if (s.panelBounds().height() <= compactPanel.height())
          throw new AssertionError("Settings did not expand the panel");
        // A recipe browser may remove the screen and later reinitialize the same object.
        s.removed();
        s.resize(mc, s.width, s.height);
        click(s, n.x() + 8, n.y() + 8);
        type(s, "draft e");
        int w = s.width, h = s.height;
        s.resize(mc, w + 20, h + 10);
        s.resize(mc, w, h);
        if (!s.charTyped('x', 0)) throw new AssertionError("Resize lost text focus");
        click(s, n.x() + 20, n.y() + 28);
      }
      case 2 -> {
        if (menu.widgetPending(name) || !nameIs(s, name, "draft ex")) return false;
        // Bypass local validation to exercise a rejected server edit and its acknowledgment.
        PacketDistributor.sendToServer(
            menu.prepareWidgetAction(name, CompanionWidgets.RENAME, "").orElseThrow());
      }
      case 3 -> {
        if (menu.widgetPending(name)) return false;
        if (!nameIs(s, name, "draft ex"))
          throw new AssertionError("Rejected edit changed server name");
        Screenshot.grab(
            mc.gameDirectory, "widget-rejection.png", mc.getMainRenderTarget(), message -> {});
        click(s, n.x() + 8, n.y() + 8);
        type(s, "discard");
        s.keyPressed(256, 0, 0); // Cancel draft, retaining the editor and screen.
        s.keyPressed(257, 0, 0);
      }
      case 4 -> {
        if (menu.widgetPending(name)) return false;
        if (!nameIs(s, name, "draft ex")) throw new AssertionError("Escape committed a draft");
        click(s, n.x() + 8, n.y() + 8);
        type(s, "hidden draft");
        var context = s.bounds(menu.bound.screen().find("panel").orElseThrow());
        click(s, context.x() + 5, context.y() + 5);
      }
      case 5 -> {
        if (s.visible(name)) return false;
        if (!s.panelBounds().equals(compactPanel))
          throw new AssertionError("Closing Settings did not restore compact panel bounds");
        if (s.charTyped('z', 0)) throw new AssertionError("Hidden widget retained keyboard focus");
        var open = s.bounds(menu.bound.screen().find("open_settings").orElseThrow());
        click(s, open.x() + 8, open.y() + 8);
      }
      case 6 -> {
        if (!s.visible(name)) return false;
        click(s, n.x() + 20, n.y() + 28); // Hidden draft must have been discarded.
      }
      case 7 -> {
        if (menu.widgetPending(name)) return false;
        if (!nameIs(s, name, "draft ex"))
          throw new AssertionError("Hidden draft survived cancellation");
        s.mouseClicked(r.x() + 8, r.y() + 20, 0);
        s.mouseDragged(r.right() + 200, r.bottom() + 100, 0, 200, 100);
        if (menu.widgetPending(slider) || ((Number) menu.value(slider, false)).intValue() != 0)
          throw new AssertionError("Drag preview was sent before release");
        s.mouseReleased(r.right() + 200, r.bottom() + 100, 0);
      }
      case 8 -> {
        if (menu.widgetPending(slider) || ((Number) menu.value(slider, false)).intValue() != 100)
          return false;
        s.mouseClicked(r.x() + 10, r.y() + 20, 0);
        s.resize(mc, s.width, s.height);
        s.mouseReleased(r.x() + 10, r.y() + 20, 0);
        if (menu.widgetPending(slider))
          throw new AssertionError("Resize committed a cancelled drag");
        s.mouseClicked(r.x() + 20, r.y() + 20, 0);
        menu.navigate("panel", "machine"); // Simulate a server-driven view change mid-drag.
        s.mouseReleased(r.x() + 20, r.y() + 20, 0);
        if (menu.widgetPending(slider)) throw new AssertionError("Hidden drag committed");
        menu.navigate("panel", "settings");
      }
      case 9 -> s.mouseScrolled(r.x() + 20, r.y() + 20, 0, -1);
      case 10 -> {
        if (menu.widgetPending(slider) || ((Number) menu.value(slider, false)).intValue() != 99)
          return false;
        s.keyPressed(258, 0, 0);
        s.keyPressed(258, 0, 0); // Text -> slider.
        s.keyPressed(263, 0, 1); // Shift-left: ten percent.
      }
      case 11 -> {
        if (menu.widgetPending(slider) || ((Number) menu.value(slider, false)).intValue() != 89)
          return false;
        click(s, l.right() - 20, l.y() + 22);
      }
      case 12 -> {
        if (menu.widgetPending(selector) || !Boolean.TRUE.equals(menu.value(selector, false)))
          return false;
        Screenshot.grab(
            mc.gameDirectory, "widget-settings.png", mc.getMainRenderTarget(), message -> {});
        click(s, l.x() + 20, l.y() + 22);
      }
      case 13 -> {
        if (menu.widgetPending(selector) || !Boolean.FALSE.equals(menu.value(selector, false)))
          return false;
        click(s, r.x() + 3, r.y() + 20); // Restore reserve for the remaining machine tests.
      }
      case 14 -> {
        if (menu.widgetPending(slider) || ((Number) menu.value(slider, false)).intValue() != 0)
          return false;
        var context = s.bounds(menu.bound.screen().find("panel").orElseThrow());
        click(s, context.x() + 5, context.y() + 5);
      }
      case 15 -> {
        if (s.visible(name)) return false;
        var open = s.bounds(menu.bound.screen().find("open_settings").orElseThrow());
        click(s, open.x() + 8, open.y() + 8);
      }
      case 16 -> {
        if (!s.visible(name)) return false;
        var delayed =
            menu.prepareWidgetAction(name, CompanionWidgets.RENAME, "Late edit").orElseThrow();
        // A recipe browser can cover the machine while its menu remains open. Let real client
        // ticks expire the request, then deliver it late to check ordinary value convergence.
        mc.setScreen(
            new net.minecraft.client.gui.screens.Screen(Component.literal("Cover")) {
              private int ticks;

              @Override
              public boolean isPauseScreen() {
                return false;
              }

              @Override
              public void tick() {
                if (++ticks < 105) return;
                if (menu.widgetPending(name))
                  throw new AssertionError("Covered screen left the editor pending");
                PacketDistributor.sendToServer(delayed);
                mc.setScreen(s);
              }
            });
      }
      case 17 -> {
        if (!nameIs(s, name, "Late edit")) return false;
        PacketDistributor.sendToServer(
            menu.prepareWidgetAction(name, CompanionWidgets.RENAME, "draft ex").orElseThrow());
      }
      case 18 -> {
        if (menu.widgetPending(name) || !nameIs(s, name, "draft ex")) return false;
        var context = s.bounds(menu.bound.screen().find("panel").orElseThrow());
        click(s, context.x() + 5, context.y() + 5);
      }
      default -> {
        if (s.visible(name)) return false;
        return true;
      }
    }
    step++;
    return false;
  }
}
