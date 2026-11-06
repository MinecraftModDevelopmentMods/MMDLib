package zone.moddev.mc.companion.test;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import zone.moddev.mc.companion.CompanionMod;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.layout.ContainerDrawerLayout;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;

/** Reopens different real machine types on the same connection, including an explicit close. */
final class DrawerMemoryClientSmoke {
  private static final BlockPos FIRST = new BlockPos(4, 100, 0), SECOND = FIRST.east(2);
  private static int step, waitTicks, reported = -1;
  private static boolean requested;

  static boolean run() {
    if (!BackpackFixture.installed()) return true;
    var mc = Minecraft.getInstance();
    if (reported != step) {
      reported = step;
      zone.moddev.mc.lib.MMDLib.LOGGER.info("DRAWER_MEMORY_STEP {}", step);
    }
    if (++waitTicks > 160) throw new AssertionError("Drawer memory timed out at step " + step);
    if (step == 0) {
      server(
          player -> {
            BackpackFixture.equip(player);
            player
                .serverLevel()
                .setBlock(FIRST, CompanionMod.MACHINES.get(0).block.get().defaultBlockState(), 3);
            player
                .serverLevel()
                .setBlock(SECOND, CompanionMod.MACHINES.get(1).block.get().defaultBlockState(), 3);
            player.teleportTo(5, 101, 1);
          });
      advance();
      return false;
    }
    if (step == 1 && !requested) {
      if (mc.level.getBlockEntity(FIRST) == null || mc.level.getBlockEntity(SECOND) == null)
        return false;
      requested = true;
      open(FIRST);
      return false;
    }
    if (!(mc.screen instanceof MachineScreen screen)) return false;
    var menu = screen.getMenu();
    boolean first = menu.blockEntity.getBlockPos().equals(FIRST);
    boolean second = menu.blockEntity.getBlockPos().equals(SECOND);
    if (!first && !second
        || menu.containerDrawer.pending()
        || menu.curiosDrawer.pending()
        || menu.automation != null && menu.automation.pending()) return false;
    var bag = menu.containerDrawer.state();
    var curios = menu.curiosDrawer.state();
    if (bag.keys().isEmpty()) return false;
    var bagLayout =
        ContainerDrawerLayout.of(
            screen.panelBounds(), screen.drawerBottom(), bag.sizes().getFirst());
    switch (step) {
      case 1 -> {
        if (!first) return false;
        if (!bag.open()) click(screen, bagLayout.tab());
        advance();
      }
      case 2 -> {
        if (!bag.open()) return false;
        open(SECOND);
        advance();
      }
      case 3 -> {
        if (!second || !bag.open()) return false;
        require(
            bag.keys().get(bag.selected()).equals("back:0"),
            "Same equipped container restored across machines");
        screen.keyPressed(256, 0, 0);
        advance();
      }
      case 4 -> {
        if (bag.open()) return false;
        open(FIRST);
        advance();
      }
      case 5 -> {
        if (!first || waitTicks < 15) return false;
        require(!bag.open(), "Explicit close is remembered on another machine");
        if (curios.icons().isEmpty()) return false;
        var layout =
            ContainerDrawerLayout.equipment(
                screen.panelBounds(), screen.drawerBottom(), curios.sizes().getFirst(), true);
        click(screen, layout.tab());
        advance();
      }
      case 6 -> {
        if (!curios.open()) return false;
        open(SECOND);
        advance();
      }
      case 7 -> {
        if (!second || !curios.open()) return false;
        require(!bag.open(), "Curios restoration keeps backpack drawer closed");
        screen.keyPressed(256, 0, 0);
        var panel = screen.panelBounds();
        click(screen, new Rect(panel.right(), panel.y() + 4, 22, 24));
        advance();
      }
      case 8 -> {
        if (curios.open()) return false;
        if (menu.automation == null) throw new AssertionError("Fixture has automation");
        if (!menu.automation.open()) {
          var panel = screen.panelBounds();
          click(screen, new Rect(panel.right(), panel.y() + 4, 22, 24));
          return false;
        }
        open(FIRST);
        advance();
      }
      case 9 -> {
        if (!first || menu.automation == null || !menu.automation.open()) return false;
        require(
            !bag.open() && !curios.open(),
            "Closed left drawers remain closed while right drawer restores");
        mc.player.closeContainer();
        advance();
        return true;
      }
      default -> {
        return true;
      }
    }
    return false;
  }

  private static void advance() {
    step++;
    waitTicks = 0;
  }

  private static void click(MachineScreen screen, Rect rect) {
    screen.mouseClicked(rect.x() + 5, rect.y() + 5, 0);
    screen.mouseReleased(rect.x() + 5, rect.y() + 5, 0);
  }

  private static void open(BlockPos pos) {
    server(player -> ScreenRuntime.open(player, player.serverLevel().getBlockEntity(pos)));
  }

  private static void server(
      java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
    var server = Minecraft.getInstance().getSingleplayerServer();
    server.execute(
        () -> {
          try {
            action.accept(server.getPlayerList().getPlayers().getFirst());
          } catch (Throwable error) {
            CompanionClientSmoke.fail(error);
          }
        });
  }

  private static void require(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }
}
