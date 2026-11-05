package zone.moddev.mc.companion.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.companion.*;
import zone.moddev.mc.lib.gui.client.MachineScreen;

/** Real client/server round trips, screenshots and item-owned editor transitions. */
final class AutomationClientSmoke {
  private static int stage;

  static boolean run(MachineScreen screen) {
    var mc = Minecraft.getInstance();
    var menu = screen.getMenu();
    var a = menu.automation;
    if (a.pending()) return false;
    if (stage == 0) {
      if (a.inventory.groups().size() != 7) throw new AssertionError("Seven colour selectors");
      a.send("select", 0, -1, "", 0, "");
      stage++;
      return false;
    }
    if (stage == 1) {
      if (!a.open()) throw new AssertionError("Colour opens drawer");
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
      a.send("editor", 0, 0, "", 0, "");
      stage++;
      return false;
    }
    if (stage == 3) {
      if (a.editor() != 0) throw new AssertionError("Filter editor opens");
      if (screen.exclusions().size() < 4) throw new AssertionError("Filter extends JEI exclusion");
      Screenshot.grab(
          mc.gameDirectory, "automation-filter-grid.png", mc.getMainRenderTarget(), m -> {});
      a.send("edit", 0, 0, "deny", 0, "");
      stage++;
      return false;
    }
    if (stage == 4) {
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
      a.send("editor", 0, 0, "", 0, "");
      stage++;
      return false;
    }
    if (stage == 6) {
      Screenshot.grab(
          mc.gameDirectory, "automation-filter-tag.png", mc.getMainRenderTarget(), m -> {});
      a.send("select", 6, -1, "", 0, "");
      stage++;
      return false;
    }
    if (stage == 7) {
      if (a.selected() != 6 || a.editor() != -1)
        throw new AssertionError("Selection closes old editor");
      Screenshot.grab(mc.gameDirectory, "automation-energy.png", mc.getMainRenderTarget(), m -> {});
      a.send("close", 6, -1, "", 0, "");
      stage++;
      return false;
    }
    return true;
  }
}
