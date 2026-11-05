package zone.moddev.mc.companion.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.companion.*;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.layout.AutomationLayout;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;

/** Real client/server round trips, screenshots and item-owned editor transitions. */
final class AutomationClientSmoke {
  private static int stage;
  private static int compactHeight;

  private static void toggleFilter(MachineScreen screen) {
    var drawer = screen.exclusions().getLast();
    var toggle = AutomationLayout.filterToggle(0).offset(drawer.x(), drawer.y());
    if (!screen.mouseClicked(toggle.x() + toggle.width() / 2, toggle.y() + toggle.height() / 2, 0))
      throw new AssertionError("Filter toggle bar accepts clicks");
  }

  static boolean renderTooltipCapture(MachineScreen screen, GuiGraphics graphics, float partial) {
    if (stage != 8 || !screen.getMenu().automation.open()) return false;
    var drawer = screen.exclusions().getLast();
    screen.renderWithTooltip(graphics, drawer.x() + 81, drawer.y() + 52, partial);
    return true;
  }

  static boolean run(MachineScreen screen) {
    var mc = Minecraft.getInstance();
    var menu = screen.getMenu();
    var a = menu.automation;
    if (screen.guiLeft() != (screen.width - screen.layout.panel().width()) / 2
        || screen.guiTop() != (screen.height - screen.layout.panel().height()) / 2)
      throw new AssertionError("Drawers must never move the centred main panel");
    if (a.pending()) return false;
    if (stage == 0) {
      if (a.inventory.groups().size() != 7) throw new AssertionError("Seven colour selectors");
      var info =
          (zone.moddev.mc.lib.gui.model.Element.Popout)
              menu.bound.screen().find("info").orElseThrow();
      if (info.edge() != zone.moddev.mc.lib.gui.model.Element.Edge.LEFT)
        throw new AssertionError("Information stays on the left");
      a.send("select", 0, -1, "", 0, "");
      stage++;
      return false;
    }
    if (stage == 1) {
      if (!a.open()) throw new AssertionError("Colour opens drawer");
      var drawer = screen.exclusions().getLast();
      var info = menu.bound.screen().find("info").orElseThrow();
      if (drawer.y() != screen.bounds(info).y())
        throw new AssertionError("Information and configuration drawers must align at the top");
      compactHeight = drawer.height();
      if (compactHeight >= screen.panelBounds().height())
        throw new AssertionError(
            "The configuration drawer should fit its controls, not the main panel height");
      Screenshot.grab(
          mc.gameDirectory, "automation-seven-colours.png", mc.getMainRenderTarget(), m -> {});
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var machine =
                    (ProcessorEntity)
                        player.serverLevel().getBlockEntity(menu.blockEntity.getBlockPos());
                machine
                    .automation
                    .slots()
                    .setStackInSlot(0, new ItemStack(CompanionAddons.PULLER.get()));
                machine
                    .automation
                    .slots()
                    .setStackInSlot(4, new ItemStack(CompanionAddons.LARGE_FILTER.get()));
                player.containerMenu.broadcastChanges();
              });
      stage++;
      return false;
    }
    if (stage == 2) {
      if (a.filter(0, 0).isEmpty()) return false;
      Screenshot.grab(
          mc.gameDirectory, "automation-filter-collapsed.png", mc.getMainRenderTarget(), m -> {});
      toggleFilter(screen);
      stage++;
      return false;
    }
    if (stage == 3) {
      if (a.editor() != 0) throw new AssertionError("Filter editor opens");
      var exclusions = screen.exclusions();
      if (exclusions.size() != 3)
        throw new AssertionError("Filter must remain inside the existing drawer exclusion");
      var drawer = exclusions.getLast();
      if (drawer.y() != screen.guiTop() + ScreenLayout.TAB_PADDING
          || drawer.height() <= compactHeight
          || drawer.bottom() <= screen.panelBounds().bottom()
          || drawer.bottom() > screen.drawerBottom())
        throw new AssertionError(
            "A large filter should grow the drawer downward within the screen");
      Screenshot.grab(
          mc.gameDirectory, "automation-filter-grid.png", mc.getMainRenderTarget(), m -> {});
      screen.resize(mc, screen.width, 360);
      var limited = screen.exclusions().getLast();
      if (limited.bottom() != screen.drawerBottom())
        throw new AssertionError("The growing drawer must stop at the screen margin");
      if (!screen.mouseScrolled(limited.x() + 40, limited.bottom() - 20, 0, -2))
        throw new AssertionError("The embedded filter grid must scroll inside the drawer");
      a.send("edit", 0, 0, "deny", 0, "");
      stage++;
      return false;
    }
    if (stage == 4) {
      Screenshot.grab(
          mc.gameDirectory, "automation-filter-constrained.png", mc.getMainRenderTarget(), m -> {});
      screen.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
      var provider =
          a.filter(0, 0).getCapability(zone.moddev.mc.lib.api.automation.AddonCapabilities.FILTER);
      if (!provider.configuration(a.filter(0, 0)).getBoolean("deny"))
        throw new AssertionError("Filter toggle synced");
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var machine =
                    (ProcessorEntity)
                        player.serverLevel().getBlockEntity(menu.blockEntity.getBlockPos());
                machine
                    .automation
                    .slots()
                    .setStackInSlot(4, new ItemStack(CompanionAddons.TAG_FILTER.get()));
                player.containerMenu.broadcastChanges();
              });
      stage++;
      return false;
    }
    if (stage == 5) {
      if (!a.filter(0, 0).is(CompanionAddons.TAG_FILTER.get()) || a.editor() != -1) return false;
      toggleFilter(screen);
      stage++;
      return false;
    }
    if (stage == 6) {
      if (screen.exclusions().getLast().height() >= screen.panelBounds().height())
        throw new AssertionError("A smaller filter should shrink the drawer to its content");
      Screenshot.grab(
          mc.gameDirectory, "automation-filter-tag.png", mc.getMainRenderTarget(), m -> {});
      a.send("select", 6, -1, "", 0, "");
      stage++;
      return false;
    }
    if (stage == 7) {
      if (a.selected() != 6 || a.editor() != -1)
        throw new AssertionError("Selection closes old editor");
      if (screen.exclusions().getLast().height() != compactHeight)
        throw new AssertionError("Changing inventory should restore the compact drawer height");
      Screenshot.grab(mc.gameDirectory, "automation-energy.png", mc.getMainRenderTarget(), m -> {});
      stage++;
      return false;
    }
    if (stage == 8) {
      Screenshot.grab(
          mc.gameDirectory, "automation-side-tooltip.png", mc.getMainRenderTarget(), m -> {});
      a.send("close", 6, -1, "", 0, "");
      stage++;
      return false;
    }
    if (stage == 9) {
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var machine =
                    (ProcessorEntity)
                        player.serverLevel().getBlockEntity(menu.blockEntity.getBlockPos());
                var filter = new ItemStack(CompanionAddons.ITEM_FILTER.get());
                var provider =
                    filter.getCapability(
                        zone.moddev.mc.lib.api.automation.AddonCapabilities.FILTER);
                var config = provider.configuration(filter);
                var entries = new net.minecraft.nbt.ListTag();
                for (String item :
                    new String[] {
                      "minecraft:diamond",
                      "minecraft:dirt",
                      "minecraft:water_bucket",
                      "minecraft:glass"
                    }) entries.add(net.minecraft.nbt.StringTag.valueOf(item));
                config.put("samples", entries);
                provider.configure(filter, config);
                machine.automation.slots().setStackInSlot(4, filter);
                player.containerMenu.broadcastChanges();
              });
      a.send("select", 0, -1, "", 0, "");
      stage++;
      return false;
    }
    if (stage == 10) {
      if (!a.filter(0, 0).is(CompanionAddons.ITEM_FILTER.get())) return false;
      toggleFilter(screen);
      stage++;
      return false;
    }
    if (stage == 11) {
      var provider =
          a.filter(0, 0).getCapability(zone.moddev.mc.lib.api.automation.AddonCapabilities.FILTER);
      var samples =
          zone.moddev.mc.lib.api.automation.FilterControl.flatten(provider.controls(a.filter(0, 0)))
              .stream()
              .filter(c -> c instanceof zone.moddev.mc.lib.api.automation.FilterControl.Samples)
              .map(c -> (zone.moddev.mc.lib.api.automation.FilterControl.Samples) c)
              .findFirst()
              .orElseThrow();
      if (samples.count() != 8)
        throw new AssertionError("Basic filter has two rows of four ghost cells");
      Screenshot.grab(
          mc.gameDirectory, "automation-filter-whitelist.png", mc.getMainRenderTarget(), m -> {});
      var drawer = screen.exclusions().getLast();
      if (!screen.mouseClicked(
          drawer.x() + AutomationLayout.EDITOR_X + 10,
          drawer.y() + AutomationLayout.EDITOR_Y + AutomationLayout.EDITOR_PADDING + 5,
          0)) throw new AssertionError("Header-free mode toggle accepts clicks");
      stage++;
      return false;
    }
    if (stage == 12) {
      var provider =
          a.filter(0, 0).getCapability(zone.moddev.mc.lib.api.automation.AddonCapabilities.FILTER);
      if (!provider.configuration(a.filter(0, 0)).getBoolean("deny"))
        throw new AssertionError(
            "Mode toggle changes whitelist to blacklist without collapsing editor");
      Screenshot.grab(
          mc.gameDirectory, "automation-filter-blacklist.png", mc.getMainRenderTarget(), m -> {});
      a.send("close", 0, -1, "", 0, "");
      stage++;
      return false;
    }
    return true;
  }
}
