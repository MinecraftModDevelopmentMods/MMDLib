package zone.moddev.mc.companion.test;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import zone.moddev.mc.companion.ProcessorEntity;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/** Deterministic real-menu captures for reserved input, occupied/empty output and mixed plans. */
final class ReservationClientSmoke {
  private static int step;
  private static volatile boolean ready;

  static boolean run(MachineScreen screen) {
    var menu = screen.getMenu();
    if (step > 0 && !ready) return false;
    switch (step) {
      case 0 ->
          server(
              player -> {
                var machine = machine(player);
                machine.lanes.forEach(lane -> lane.reset());
                machine.setReservePercent(100);
                machine.energy.setStored(0);
                machine.items.setStackInSlot(0, new ItemStack(Items.COPPER_ORE, 10));
                machine.items.setStackInSlot(1, new ItemStack(Items.RAW_COPPER, 10));
                machine.items.setStackInSlot(2, ItemStack.EMPTY);
                machine.items.setPlan(0, new SlotPlan(2, ItemStack.EMPTY));
                machine.items.setPlan(1, new SlotPlan(0, new ItemStack(Items.RAW_COPPER)));
              });
      case 1 -> {
        if (menu.reservationPlan(0).take() != 2
            || menu.getSlot(0).getItem().getCount() != 10
            || menu.getSlot(1).getItem().getCount() != 10
            || menu.reservationPlan(1).add().getCount() != 1) return false;
        int playerSlot =
            menu.slots.stream()
                .filter(slot -> slot.container == Minecraft.getInstance().player.getInventory())
                .findFirst()
                .orElseThrow()
                .index;
        require(menu.reservationPlan(playerSlot).isNone(), "Player slots have no overlay");
        SlotTooltipClientSmoke.check(screen, 0, true);
        capture("reservations-counts.png");
        server(player -> machine(player).items.setStackInSlot(1, ItemStack.EMPTY));
      }
      case 2 -> {
        if (!menu.getSlot(1).getItem().isEmpty() || menu.reservationPlan(1).add().isEmpty())
          return false;
        SlotTooltipClientSmoke.check(screen, 1, false);
        capture("reservations-ghost.png");
        var slot = menu.getSlot(1);
        double x = screen.panelBounds().x() + slot.x + 8;
        double y = screen.panelBounds().y() + slot.y + 8;
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
      }
      case 3 -> {
        require(
            menu.getCarried().isEmpty() && menu.getSlot(1).getItem().isEmpty(),
            "Clicking a ghost does not create or pick up a real item");
        server(
            player -> {
              require(
                  player.containerMenu.getCarried().isEmpty(), "Server sees no ghost item pickup");
              machine(player).items.setPlan(0, SlotPlan.NONE);
              machine(player).items.setPlan(1, SlotPlan.NONE);
            });
      }
      case 4 -> {
        if (!menu.reservationPlan(0).isNone() || !menu.reservationPlan(1).isNone()) return false;
        require(
            menu.getSlot(0).mayPlace(new ItemStack(Items.COPPER_ORE)),
            "Client recipe sync admits a valid ingredient before a recipe starts");
        require(
            !menu.getSlot(0).mayPlace(new ItemStack(Items.DIAMOND)),
            "Client prediction rejects unrelated items without relying on reservations");
        capture("reservations-cleared.png");
        server(
            player ->
                machine(player)
                    .items
                    .setPlan(0, new SlotPlan(10, new ItemStack(Items.GOLD_INGOT))));
      }
      case 5 -> {
        if (menu.reservationPlan(0).take() != 10
            || !menu.reservationPlan(0).add().is(Items.GOLD_INGOT)) return false;
        capture("reservations-mixed.png");
        server(
            player -> {
              var machine = machine(player);
              machine.items.setPlan(0, SlotPlan.NONE);
              machine.items.setPlan(1, SlotPlan.NONE);
              machine.items.setStackInSlot(0, new ItemStack(Items.IRON_ORE));
              machine.items.setStackInSlot(1, new ItemStack(Items.RAW_IRON, 63));
              machine.items.setStackInSlot(2, new ItemStack(Items.RAW_IRON, 63));
              machine.setReservePercent(0);
              machine.energy.setStored(80000);
              machine.serverTick();
              machine.setReservePercent(100);
              machine.energy.setStored(0);
            });
      }
      case 6 -> {
        if (menu.reservationPlan(0).take() != 1
            || menu.reservationPlan(1).add().getCount() != 1
            || menu.reservationPlan(2).add().getCount() != 1
            || menu.getSlot(1).getItem().getCount() != 63
            || menu.getSlot(2).getItem().getCount() != 63) return false;
        require(menu.getSlot(2).isActive(), "Second crusher output is visible and interactive");
        var output = ((ProcessorEntity) menu.blockEntity).automation.group(1);
        require(
            output.inventory().equals("output") && output.members().equals(java.util.List.of(0, 1)),
            "Both outputs share the same colour group");
        capture("crusher-split-output-reservations.png");
        server(
            player -> {
              var machine = machine(player);
              machine.setReservePercent(0);
              machine.energy.setStored(80000);
              for (int i = 0; i < 100; i++) machine.serverTick();
            });
      }
      case 7 -> {
        if (!menu.getSlot(0).getItem().isEmpty()
            || menu.getSlot(1).getItem().getCount() != 64
            || menu.getSlot(2).getItem().getCount() != 64) return false;
        require(
            menu.reservationPlan(1).isNone() && menu.reservationPlan(2).isNone(),
            "Both completed output reservations clear on the client");
        capture("crusher-split-output-completed.png");
        server(
            player -> {
              var machine = machine(player);
              machine.items.setStackInSlot(0, new ItemStack(Items.COPPER_ORE, 16));
              machine.items.setStackInSlot(1, ItemStack.EMPTY);
              machine.items.setStackInSlot(2, ItemStack.EMPTY);
            });
      }
      default -> {
        return true;
      }
    }
    step++;
    return false;
  }

  private static ProcessorEntity machine(ServerPlayer player) {
    return (ProcessorEntity) ((MachineMenu) player.containerMenu).blockEntity;
  }

  private static void server(Consumer<ServerPlayer> action) {
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

  private static void capture(String file) {
    var mc = Minecraft.getInstance();
    Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), message -> {});
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
