package zone.moddev.mc.lib.smoke;

import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import zone.moddev.mc.lib.gui.runtime.*;

/** Conservation, filter, replay and off-page checks for batched backpack gestures. */
final class ContainerInteractionSmoke {
  static void check(MachineMenu menu, Player player) {
    player.getInventory().clearContent();
    var items =
        new ItemStackHandler(70) {
          public int getSlotLimit(int slot) {
            return slot == 1 ? 4 : 64;
          }

          public boolean isItemValid(int slot, ItemStack stack) {
            return slot != 2 && !stack.is(Items.DIRT);
          }

          public ItemStack extractItem(int slot, int count, boolean simulate) {
            return slot == 3 ? ItemStack.EMPTY : super.extractItem(slot, count, simulate);
          }
        };
    var icon = new ItemStack(Items.CHEST);
    var drawer =
        new ContainerDrawerMenu(
            menu, player, () -> List.of(new EquippedContainers.Source("back:1", icon, items)));
    send(drawer, menu, player, ContainerDrawerMenu.TOGGLE, 0, List.of());
    menu.setCarried(new ItemStack(Items.IRON_INGOT, 20));
    send(drawer, menu, player, ContainerDrawerMenu.DISTRIBUTE, 0, List.of(0, 1, 2, 4096));
    require(
        items.getStackInSlot(0).getCount() == 6
            && items.getStackInSlot(1).getCount() == 4
            && items.getStackInSlot(2).isEmpty()
            && player.getInventory().getItem(0).getCount() == 6
            && menu.getCarried().getCount() == 4,
        "Even drag respects filters, limits and remainder");
    send(drawer, menu, player, ContainerDrawerMenu.DISTRIBUTE, 1, List.of(0, 4));
    require(
        items.getStackInSlot(0).getCount() == 7
            && items.getStackInSlot(4).getCount() == 1
            && menu.getCarried().getCount() == 2,
        "Right drag places one per target");
    send(drawer, menu, player, ContainerDrawerMenu.DISTRIBUTE, 0, List.of(0, 0));
    send(drawer, menu, player, ContainerDrawerMenu.DISTRIBUTE, 0, List.of(0, 60));
    require(
        menu.getCarried().getCount() == 2, "Duplicate and off-page targets reject whole gesture");
    drawer.refresh();
    var request = request(drawer, menu, ContainerDrawerMenu.DISTRIBUTE, 1, List.of(0, 4));
    drawer.action(player, request);
    drawer.action(player, request);
    require(
        menu.getCarried().isEmpty()
            && items.getStackInSlot(0).getCount() == 8
            && items.getStackInSlot(4).getCount() == 2,
        "Replayed drag cannot duplicate items");
    menu.setCarried(new ItemStack(Items.IRON_INGOT, 1));
    items.setStackInSlot(3, new ItemStack(Items.IRON_INGOT, 9));
    items.setStackInSlot(60, new ItemStack(Items.IRON_INGOT, 20));
    var named = new ItemStack(Items.IRON_INGOT, 5);
    named.set(
        net.minecraft.core.component.DataComponents.CUSTOM_NAME,
        net.minecraft.network.chat.Component.literal("Keep"));
    items.setStackInSlot(5, named);
    send(drawer, menu, player, ContainerDrawerMenu.COLLECT, 0, List.of());
    require(
        menu.getCarried().getCount() == 41
            && items.getStackInSlot(60).isEmpty()
            && player.getInventory().getItem(0).isEmpty()
            && items.getStackInSlot(3).getCount() == 9
            && ItemStack.matches(items.getStackInSlot(5), named),
        "Collect includes other pages and player slots but honors components/extraction");
    items.setStackInSlot(61, new ItemStack(Items.IRON_INGOT, 50));
    send(drawer, menu, player, ContainerDrawerMenu.COLLECT, 0, List.of());
    require(
        menu.getCarried().getCount() == 64 && items.getStackInSlot(61).getCount() == 27,
        "Collect caps cursor stack");
    menu.setCarried(ItemStack.EMPTY);
    player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 2));
    items.setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 5));
    send(drawer, menu, player, ContainerDrawerMenu.SWAP, 0, List.of(0));
    require(
        items.getStackInSlot(0).is(Items.DIAMOND)
            && items.getStackInSlot(0).getCount() == 2
            && player.getInventory().getItem(0).is(Items.COPPER_INGOT)
            && player.getInventory().getItem(0).getCount() == 5,
        "Hotbar swap exchanges whole stacks");
    player.getInventory().setItem(0, new ItemStack(Items.DIRT, 3));
    send(drawer, menu, player, ContainerDrawerMenu.SWAP, 0, List.of(0));
    require(
        items.getStackInSlot(0).is(Items.DIAMOND)
            && player.getInventory().getItem(0).getCount() == 3,
        "Swap rejects invalid insertion");
    player.getInventory().setItem(0, ItemStack.EMPTY);
    send(drawer, menu, player, ContainerDrawerMenu.SWAP, 0, List.of(3));
    require(player.getInventory().getItem(0).isEmpty(), "Swap honors locked extraction");
    send(drawer, menu, player, ContainerDrawerMenu.SWAP, 0, List.of(0));
    require(
        items.getStackInSlot(0).isEmpty() && player.getInventory().getItem(0).is(Items.DIAMOND),
        "Swap into empty hotbar");
    drawer.refresh();
    var stale = request(drawer, menu, ContainerDrawerMenu.SWAP, 0, List.of(0));
    items.setStackInSlot(6, new ItemStack(Items.STONE));
    drawer.action(player, stale);
    require(
        items.getStackInSlot(0).is(Items.DIAMOND) && player.getInventory().getItem(0).isEmpty(),
        "An unrelated live slot update does not reject a hotbar swap");
    var rejecting =
        new ItemStackHandler(1) {
          public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack.is(Items.DIAMOND) ? stack : super.insertItem(slot, stack, simulate);
          }
        };
    rejecting.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 5));
    var rollback =
        new ContainerDrawerMenu(
            menu, player, () -> List.of(new EquippedContainers.Source("back:2", icon, rejecting)));
    player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 2));
    send(rollback, menu, player, ContainerDrawerMenu.TOGGLE, 0, List.of());
    send(rollback, menu, player, ContainerDrawerMenu.SWAP, 0, List.of(0));
    require(
        rejecting.getStackInSlot(0).getCount() == 5
            && rejecting.getStackInSlot(0).is(Items.IRON_INGOT)
            && player.getInventory().getItem(0).getCount() == 2
            && player.getInventory().getItem(0).is(Items.DIAMOND),
        "Rejected swap restores the extracted stack without consuming incoming items");
    var partial =
        new ItemStackHandler(1) {
          public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!simulate && stack.is(Items.DIAMOND)) {
              var left =
                  super.insertItem(slot, stack.copyWithCount(Math.min(2, stack.getCount())), false);
              return stack.copyWithCount(
                  stack.getCount() - Math.min(2, stack.getCount()) + left.getCount());
            }
            return super.insertItem(slot, stack, simulate);
          }
        };
    partial.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 5));
    var partialDrawer =
        new ContainerDrawerMenu(
            menu, player, () -> List.of(new EquippedContainers.Source("back:3", icon, partial)));
    player.getInventory().clearContent();
    player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 4));
    send(partialDrawer, menu, player, ContainerDrawerMenu.TOGGLE, 0, List.of());
    send(partialDrawer, menu, player, ContainerDrawerMenu.SWAP, 0, List.of(0));
    require(
        partial.getStackInSlot(0).getCount() == 2
            && player.getInventory().getItem(0).getCount() == 2
            && player.getInventory().getItem(1).is(Items.IRON_INGOT)
            && player.getInventory().getItem(1).getCount() == 5,
        "Partial swap preserves both remainders");
    var buffer =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), player.registryAccess());
    try {
      ContainerDrawerNetwork.Request.CODEC.encode(
          buffer, request(drawer, menu, ContainerDrawerMenu.DISTRIBUTE, 0, List.of(0, 4096)));
      require(
          ContainerDrawerNetwork.Request.CODEC.decode(buffer).slots().equals(List.of(0, 4096)),
          "Gesture codec retains bounded targets");
    } finally {
      buffer.release();
    }
    menu.setCarried(ItemStack.EMPTY);
    player.getInventory().clearContent();
  }

  private static ContainerDrawerNetwork.Request request(
      ContainerDrawerMenu drawer, MachineMenu menu, int kind, int arg, List<Integer> slots) {
    return new ContainerDrawerNetwork.Request(
        menu.containerId,
        false,
        menu.widgetSession(),
        drawer.state().revision(),
        drawer.state().acknowledged() + 1,
        kind,
        arg,
        27,
        menu.getCarried().copy(),
        slots);
  }

  private static void send(
      ContainerDrawerMenu drawer,
      MachineMenu menu,
      Player player,
      int kind,
      int arg,
      List<Integer> slots) {
    drawer.refresh();
    drawer.action(player, request(drawer, menu, kind, arg, slots));
  }

  private static void require(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }
}
