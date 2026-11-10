package zone.moddev.mc.companion.test;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.runtime.EquippedContainers;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;

/** Real mouse gestures and hotbar keys, including a double-click before pickup acknowledgement. */
final class BackpackInteractionClientSmoke {
  private static int step, reported = -1;
  private static volatile boolean ready;

  static boolean run(MachineScreen screen) {
    var mc = Minecraft.getInstance();
    var menu = screen.getMenu();
    var state = menu.components().get(BuiltinMenuComponents.BACKPACK).state();
    if (reported != step) {
      reported = step;
      zone.moddev.mc.lib.MMDLib.LOGGER.info("BACKPACK_INTERACTION_STEP {}", step);
    }
    if (menu.components().get(BuiltinMenuComponents.BACKPACK).pending()) return false;
    var layout = DrawerExtensionClientSmoke.containerLayout(screen, false);
    switch (step) {
      case 0 ->
          setup(
              player -> {
                var handler = EquippedContainers.find(player).getFirst().items();
                for (int i = 0; i < handler.getSlots(); i++)
                  handler.extractItem(i, Integer.MAX_VALUE, false);
                player.getInventory().clearContent();
                player.containerMenu.setCarried(new ItemStack(Items.IRON_INGOT, 10));
              });
      case 1 -> {
        if (!ready || menu.getCarried().getCount() != 10) return false;
        drag(screen, 0, layout.cell(0), layout.cell(3), layout.cell(4));
      }
      case 2 -> {
        require(
            menu.getCarried().getCount() == 1 && state.contents().get(0).getCount() == 9,
            "Left drag preserves remainder while the capability routes insertions");
        setup(player -> player.containerMenu.setCarried(new ItemStack(Items.IRON_INGOT, 5)));
      }
      case 3 -> {
        if (!ready || menu.getCarried().getCount() != 5) return false;
        drag(screen, 1, layout.cell(5), layout.cell(6));
      }
      case 4 -> {
        require(
            menu.getCarried().getCount() == 3 && state.contents().get(0).getCount() == 11,
            "Right drag inserts one per target while respecting capability routing");
        setup(
            player -> {
              player.containerMenu.setCarried(ItemStack.EMPTY);
              var handler = EquippedContainers.find(player).getFirst().items();
              ((net.neoforged.neoforge.items.IItemHandlerModifiable) handler)
                  .setStackInSlot(7, new ItemStack(Items.IRON_INGOT, 10));
              ((net.neoforged.neoforge.items.IItemHandlerModifiable) handler)
                  .setStackInSlot(8, new ItemStack(Items.IRON_INGOT, 20));
              player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 12));
            });
      }
      case 5 -> {
        if (!ready || !menu.getCarried().isEmpty() || state.contents().get(7).getCount() != 10)
          return false;
        click(screen, layout.cell(7));
        click(screen, layout.cell(7));
      }
      case 6 -> {
        require(
            menu.getCarried().getCount() == 53,
            "Collection cursor=" + menu.getCarried() + ", contents=" + state.contents());
        require(
            state.contents().stream().noneMatch(s -> s.is(Items.IRON_INGOT))
                && mc.player.getInventory().getItem(9).isEmpty(),
            "Double click collects backpack and player stacks");
        setup(
            player -> {
              player.containerMenu.setCarried(ItemStack.EMPTY);
              player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 2));
              ((net.neoforged.neoforge.items.IItemHandlerModifiable)
                      EquippedContainers.find(player).getFirst().items())
                  .setStackInSlot(9, new ItemStack(Items.COPPER_INGOT, 5));
            });
      }
      case 7 -> {
        if (!ready
            || !menu.getCarried().isEmpty()
            || !mc.player.getInventory().getItem(0).is(Items.DIAMOND)) return false;
        screen.mouseMoved(layout.cell(9).x() + 5, layout.cell(9).y() + 5);
        require(screen.keyPressed(49, 0, 0), "Number key handled over backpack");
      }
      case 8 -> {
        require(
            state.contents().stream()
                        .filter(s -> s.is(Items.DIAMOND))
                        .mapToInt(ItemStack::getCount)
                        .sum()
                    == 2
                && mc.player.getInventory().getItem(0).is(Items.COPPER_INGOT)
                && mc.player.getInventory().getItem(0).getCount() == 5,
            "Number key swaps with hotbar");
        setup(
            player -> {
              player.getInventory().clearContent();
              player.containerMenu.setCarried(new ItemStack(Items.IRON_INGOT, 10));
            });
      }
      case 9 -> {
        if (!ready || menu.getCarried().getCount() != 10) return false;
        beginDrag(screen, 0, playerCell(screen, 9), playerCell(screen, 10), playerCell(screen, 11));
      }
      case 10 -> release(screen, 0, playerCell(screen, 11));
      case 11 -> {
        require(
            menu.getCarried().getCount() == 1
                && mc.player.getInventory().getItem(9).getCount() == 3
                && mc.player.getInventory().getItem(10).getCount() == 3
                && mc.player.getInventory().getItem(11).getCount() == 3,
            "Normal inventory left drag survives frames and release with backpack open");
        setup(
            player -> {
              player.getInventory().clearContent();
              player.containerMenu.setCarried(new ItemStack(Items.IRON_INGOT, 5));
            });
      }
      case 12 -> {
        if (!ready || menu.getCarried().getCount() != 5) return false;
        beginDrag(screen, 1, playerCell(screen, 12), playerCell(screen, 13));
      }
      case 13 -> release(screen, 1, playerCell(screen, 13));
      case 14 -> {
        require(
            menu.getCarried().getCount() == 3
                && mc.player.getInventory().getItem(12).getCount() == 1
                && mc.player.getInventory().getItem(13).getCount() == 1,
            "Normal inventory right drag survives frames and release with backpack open");
        setup(
            player -> {
              player.containerMenu.setCarried(ItemStack.EMPTY);
              player.getInventory().clearContent();
              player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 10));
              player.getInventory().setItem(10, new ItemStack(Items.IRON_INGOT, 20));
            });
      }
      case 15 -> {
        if (!ready
            || !menu.getCarried().isEmpty()
            || mc.player.getInventory().getItem(9).getCount() != 10) return false;
        click(screen, playerCell(screen, 9));
        click(screen, playerCell(screen, 9));
      }
      case 16 -> {
        require(
            menu.getCarried().getCount() == 30
                && mc.player.getInventory().getItem(9).isEmpty()
                && mc.player.getInventory().getItem(10).isEmpty(),
            "Normal inventory double click collects with backpack open");
        setup(player -> player.containerMenu.setCarried(new ItemStack(Items.IRON_INGOT, 10)));
      }
      case 17 -> {
        if (!ready || menu.getCarried().getCount() != 10) return false;
        beginDrag(screen, 0, layout.cell(3), playerCell(screen, 9), playerCell(screen, 10));
        setup(
            player ->
                ((net.neoforged.neoforge.items.IItemHandlerModifiable)
                        EquippedContainers.find(player).getFirst().items())
                    .setStackInSlot(1, new ItemStack(Items.COPPER_INGOT, 13)));
      }
      case 18 -> {
        if (!ready || state.contents().get(1).getCount() != 13) return false;
        release(screen, 0, playerCell(screen, 10));
      }
      case 19 -> {
        require(
            menu.getCarried().getCount() == 1
                && mc.player.getInventory().getItem(9).getCount() == 3
                && mc.player.getInventory().getItem(10).getCount() == 3
                && state.contents().stream()
                        .filter(s -> s.is(Items.IRON_INGOT))
                        .mapToInt(ItemStack::getCount)
                        .sum()
                    == 3,
            "Backpack drag into player slots survives frames and release");
        setup(
            player -> {
              var handler = EquippedContainers.find(player).getFirst().items();
              for (int i = 0; i < handler.getSlots(); i++)
                handler.extractItem(i, Integer.MAX_VALUE, false);
              // Seed the fixture's exact slots; production intentionally uses only I/O.
              ((net.neoforged.neoforge.items.IItemHandlerModifiable) handler)
                  .setStackInSlot(1, new ItemStack(Items.COPPER_INGOT, 12));
              player.getInventory().clearContent();
              player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 24));
              player.containerMenu.setCarried(ItemStack.EMPTY);
            });
      }
      default -> {
        return ready
            && state.contents().get(1).getCount() == 12
            && state.contents().get(9).isEmpty();
      }
    }
    step++;
    return false;
  }

  private static void setup(
      java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
    ready = false;
    var server = Minecraft.getInstance().getSingleplayerServer();
    server.execute(
        () -> {
          try {
            var player = server.getPlayerList().getPlayers().getFirst();
            action.accept(player);
            player.containerMenu.broadcastChanges();
            ready = true;
          } catch (Throwable error) {
            CompanionClientSmoke.fail(error);
          }
        });
  }

  private static void drag(MachineScreen screen, int button, Rect... cells) {
    beginDrag(screen, button, cells);
    release(screen, button, cells[cells.length - 1]);
  }

  private static void beginDrag(MachineScreen screen, int button, Rect... cells) {
    var first = cells[0];
    require(screen.mouseClicked(first.x() + 5, first.y() + 5, button), "Drag press handled");
    for (var cell : cells) screen.mouseDragged(cell.x() + 5, cell.y() + 5, button, 18, 0);
  }

  private static void release(MachineScreen screen, int button, Rect last) {
    require(screen.mouseReleased(last.x() + 5, last.y() + 5, button), "Drag release handled");
  }

  private static Rect playerCell(MachineScreen screen, int index) {
    var inventory = Minecraft.getInstance().player.getInventory();
    var slot =
        screen.getMenu().slots.stream()
            .filter(s -> s.container == inventory && s.getContainerSlot() == index)
            .findFirst()
            .orElseThrow();
    return new Rect(screen.guiLeft() + slot.x, screen.guiTop() + slot.y, 16, 16);
  }

  private static void click(MachineScreen screen, Rect cell) {
    screen.mouseClicked(cell.x() + 5, cell.y() + 5, 0);
    screen.mouseReleased(cell.x() + 5, cell.y() + 5, 0);
  }

  private static void require(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }
}
