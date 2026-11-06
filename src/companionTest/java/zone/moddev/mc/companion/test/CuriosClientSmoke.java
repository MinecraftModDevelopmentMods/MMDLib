package zone.moddev.mc.companion.test;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.layout.ContainerDrawerLayout;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.runtime.ContainerDrawerMenu;

/** Networked functional/cosmetic equipment, optional absence, paging and dynamic-slot checks. */
final class CuriosClientSmoke {
  private static int step, originalSize;
  private static int reportedStep = -1;
  private static volatile boolean staleChecked;

  static boolean run(MachineScreen screen) {
    var mc = Minecraft.getInstance();
    var menu = screen.getMenu();
    var drawer = menu.curiosDrawer;
    if (reportedStep != step) {
      reportedStep = step;
      zone.moddev.mc.lib.MMDLib.LOGGER.info("CURIOS_DRAWER_STEP {}", step);
    }
    if (!ModList.get().isLoaded("curios")) {
      require(drawer.state().icons().isEmpty(), "Curios is optional on the client");
      return true;
    }
    if (step == 20) return !drawer.state().open();
    if (drawer.pending() || menu.containerDrawer.pending()) return false;
    var state = drawer.state();
    if (state.icons().isEmpty()) return false;
    var layout =
        ContainerDrawerLayout.equipment(
            screen.panelBounds(),
            screen.drawerBottom(),
            state.sizes().getFirst(),
            !menu.containerDrawer.state().icons().isEmpty());
    require(
        layout.pageSize() > 0 && layout.body().height() == screen.panelBounds().height() - 8,
        "Full-height equipment drawer fits");
    switch (step) {
      case 0 -> {
        var bag =
            ContainerDrawerLayout.of(
                screen.panelBounds(),
                screen.drawerBottom(),
                menu.containerDrawer.state().sizes().getFirst());
        click(screen, bag.tab(), 0);
        step++;
      }
      case 1 -> {
        if (!menu.containerDrawer.state().open()) return false;
        click(screen, layout.tab(), 0);
        step++;
      }
      case 2 -> {
        if (!state.open() || state.pageSize() != layout.pageSize()) return false;
        require(!menu.containerDrawer.state().open(), "Opening Curios closes backpack storage");
        require(
            state.descriptions().getFirst().identifier().equals("ring")
                && !state.descriptions().getFirst().cosmetic(),
            "Slot groups use registered order");
        require(
            state.contents().get(0).is(Items.DIAMOND) && state.contents().get(1).is(Items.EMERALD),
            "Functional and cosmetic contents are distinct");
        require(
            !state.descriptions().get(7).available(), "Missing cosmetic counterpart is disabled");
        require(
            screen.exclusions().contains(layout.body())
                && screen.exclusions().contains(layout.rail()),
            "Equipment body and rail are excluded from JEI");
        Screenshot.grab(
            mc.gameDirectory, "curios-drawer.png", mc.getMainRenderTarget(), message -> {});
        click(screen, layout.cell(0), 0);
        originalSize = state.sizes().getFirst();
        step++;
      }
      case 3 -> {
        if (!menu.getCarried().is(Items.DIAMOND)) return false;
        require(state.contents().get(0).isEmpty(), "Unequip functional item");
        click(screen, layout.cell(1), 0);
        step++;
      }
      case 4 -> {
        require(
            menu.getCarried().is(Items.DIAMOND) && state.contents().get(1).is(Items.EMERALD),
            "Incompatible swap does not lose either item");
        click(screen, layout.cell(0), 0);
        step++;
      }
      case 5 -> {
        if (!menu.getCarried().isEmpty()) return false;
        require(state.contents().get(0).is(Items.DIAMOND), "Re-equip functional item");
        click(screen, layout.cell(1), 0);
        step++;
      }
      case 6 -> {
        if (!menu.getCarried().is(Items.EMERALD)) return false;
        require(state.contents().get(1).isEmpty(), "Unequip cosmetic item");
        click(screen, layout.cell(1), 0);
        step++;
      }
      case 7 -> {
        if (!menu.getCarried().isEmpty()) return false;
        require(state.contents().get(1).is(Items.EMERALD), "Re-equip cosmetic item");
        drawer
            .request(ContainerDrawerMenu.QUICK_TAKE, 0, state.pageSize())
            .ifPresent(PacketDistributor::sendToServer);
        step++;
      }
      case 8 -> {
        if (!state.contents().get(0).isEmpty()) return false;
        int index = -1;
        for (int i = 0; i < 36; i++)
          if (mc.player.getInventory().getItem(i).is(Items.DIAMOND)) {
            index = i;
            break;
          }
        require(index >= 0, "Shift unequip reaches player inventory");
        drawer
            .request(ContainerDrawerMenu.QUICK_STORE, index, state.pageSize())
            .ifPresent(PacketDistributor::sendToServer);
        step++;
      }
      case 9 -> {
        if (!state.contents().get(0).is(Items.DIAMOND)) return false;
        server(
            player -> {
              player.containerMenu.setCarried(new ItemStack(Items.STONE));
              player.containerMenu.broadcastChanges();
            });
        step++;
      }
      case 10 -> {
        if (!menu.getCarried().is(Items.STONE)) return false;
        click(screen, layout.cell(4), 0);
        step++;
      }
      case 11 -> {
        require(
            menu.getCarried().is(Items.STONE) && state.contents().get(4).isEmpty(),
            "Curios slot validation rejects wrong items");
        server(
            player -> {
              player.containerMenu.setCarried(ItemStack.EMPTY);
              player.containerMenu.broadcastChanges();
            });
        step++;
      }
      case 12 -> {
        if (!menu.getCarried().isEmpty()) return false;
        click(screen, layout.cell(2), 0);
        step++;
      }
      case 13 -> {
        require(
            menu.getCarried().isEmpty() && state.contents().get(2).is(Items.DIAMOND),
            "Curios unequip events can forbid removal");
        click(screen, layout.next(), 0);
        step++;
      }
      case 14 -> {
        if (state.offset() == 0) return false;
        if (state.contents().stream().noneMatch(stack -> stack.is(Items.AMETHYST_SHARD))) {
          require(
              state.offset() + state.pageSize() < state.sizes().getFirst(),
              "Later equipment page contains its actual items");
          click(screen, layout.next(), 0);
          return false;
        }
        Screenshot.grab(
            mc.gameDirectory, "curios-page-two.png", mc.getMainRenderTarget(), message -> {});
        click(screen, layout.previous(), 0);
        step++;
      }
      case 15 -> {
        if (state.offset() != 0) {
          click(screen, layout.previous(), 0);
          return false;
        }
        server(CuriosFixture::resize);
        step++;
      }
      case 16 -> {
        if (state.sizes().getFirst() != originalSize + 2) return false;
        require(!state.open(), "Changing slot count closes the old equipment view");
        server(
            player -> {
              CuriosFixture.checkStale(player);
              staleChecked = true;
            });
        step++;
      }
      case 17 -> {
        if (!staleChecked) return false;
        click(screen, layout.tab(), 0);
        step++;
      }
      case 18 -> {
        if (!state.open() || state.pageSize() != layout.pageSize()) return false;
        require(
            state.descriptions().get(6).identifier().equals("ring")
                && state.descriptions().get(6).index() == 3,
            "New equipment slots appear");
        require(screen.keyPressed(256, 0, 0), "Escape closes equipment drawer");
        step++;
      }
      case 19 -> {
        if (state.open()) return false;
        require(mc.screen == screen, "Closing drawer retains machine screen");
        step++;
      }
      default -> throw new AssertionError("Unexpected Curios smoke step");
    }
    return false;
  }

  private static void server(Consumer<ServerPlayer> action) {
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

  private static void click(MachineScreen screen, Rect rect, int button) {
    require(screen.mouseClicked(rect.x() + 5, rect.y() + 5, button), "Curios click handled");
    screen.mouseReleased(rect.x() + 5, rect.y() + 5, button);
  }

  private static void require(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }
}
