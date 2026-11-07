package zone.moddev.mc.lib.smoke;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import zone.moddev.mc.lib.gui.runtime.ContainerDrawerMenu;
import zone.moddev.mc.lib.gui.runtime.ContainerDrawerNetwork;
import zone.moddev.mc.lib.gui.runtime.EquippedContainers;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;

/** Exercises authoritative transfers through a capability that deliberately has no setter API. */
final class ContainerDrawerSmoke {
  static void check(ServerLevel level, BlockPos pos, Player player) {
    require(!net.neoforged.fml.ModList.get().isLoaded("curios"), "Standalone run has no Curios");
    require(EquippedContainers.find(player).isEmpty(), "Absent optional API is safe");
    level.setBlock(pos, BuiltInRegistries.BLOCK.get(CrusherSmoke.ID).defaultBlockState(), 3);
    player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
    var menu = new MachineMenu(93, player.getInventory(), level.getBlockEntity(pos));
    player.containerMenu = menu;
    menu.curiosDrawer.refresh();
    require(
        menu.curiosDrawer.state().equipment() && menu.curiosDrawer.state().icons().isEmpty(),
        "Equipment controller loads safely with Curios absent");
    player.getInventory().clearContent();
    var backing = new ItemStackHandler(70);
    var handler =
        new IItemHandler() {
          public int getSlots() {
            return backing.getSlots();
          }

          public ItemStack getStackInSlot(int slot) {
            return backing.getStackInSlot(slot);
          }

          public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return isItemValid(slot, stack) ? backing.insertItem(slot, stack, simulate) : stack;
          }

          public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 2 ? ItemStack.EMPTY : backing.extractItem(slot, amount, simulate);
          }

          public int getSlotLimit(int slot) {
            return backing.getSlotLimit(slot);
          }

          public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(Items.IRON_INGOT);
          }
        };
    var icon = new ItemStack(Items.LEATHER_CHESTPLATE);
    icon.set(DataComponents.CUSTOM_NAME, Component.literal("Actual equipped item"));
    var sources = new ArrayList<>(List.of(new EquippedContainers.Source("back:0", icon, handler)));
    var drawer = new ContainerDrawerMenu(menu, player, () -> sources);
    drawer.refresh();
    require(
        ItemStack.matches(icon, drawer.state().icons().getFirst()), "Icon preserves components");
    var unchanged = drawer.state();
    drawer.refresh();
    require(drawer.state() == unchanged, "Unchanged closed drawer reuses its snapshot");
    backing.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 15));
    send(drawer, menu, player, ContainerDrawerMenu.PICKUP, 0);
    require(menu.getCarried().isEmpty(), "Closed drawer cannot transfer");
    send(drawer, menu, player, ContainerDrawerMenu.TOGGLE, 0);
    unchanged = drawer.state();
    drawer.refresh();
    require(drawer.state() == unchanged, "Unchanged open drawer reuses its snapshot");
    var oldContents = unchanged.contents();
    backing.getStackInSlot(0).grow(1);
    drawer.refresh();
    require(
        drawer.state() != unchanged && oldContents.getFirst().getCount() == 15,
        "In-place count changes refresh without mutating previous snapshots");
    backing.getStackInSlot(0).shrink(1);
    drawer.refresh();
    unchanged = drawer.state();
    icon.set(DataComponents.CUSTOM_NAME, Component.literal("Renamed equipped item"));
    drawer.refresh();
    require(
        drawer.state() != unchanged && drawer.state().contents() == unchanged.contents(),
        "Icon changes reuse unchanged content copies");

    send(drawer, menu, player, ContainerDrawerMenu.PICKUP, 1);
    require(
        menu.getCarried().getCount() == 8 && backing.getStackInSlot(0).getCount() == 7,
        "Right pickup splits");
    send(drawer, menu, player, ContainerDrawerMenu.PICKUP, 1);
    require(
        menu.getCarried().getCount() == 7 && backing.getStackInSlot(0).getCount() == 8,
        "Right insert places one");
    backing.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 62));
    send(drawer, menu, player, ContainerDrawerMenu.PICKUP, 0);
    require(
        menu.getCarried().getCount() == 5 && backing.getStackInSlot(0).getCount() == 64,
        "Partial insertion keeps remainder");
    menu.setCarried(new ItemStack(Items.DIAMOND, 3));
    send(drawer, menu, player, ContainerDrawerMenu.PICKUP, 2);
    require(
        menu.getCarried().getCount() == 3 && backing.getStackInSlot(1).isEmpty(),
        "Insertion filter honored");
    menu.setCarried(ItemStack.EMPTY);
    backing.setStackInSlot(2, new ItemStack(Items.IRON_INGOT, 9));
    send(drawer, menu, player, ContainerDrawerMenu.PICKUP, 4);
    require(menu.getCarried().isEmpty(), "Extraction filter honored");
    var live = request(drawer, menu, ContainerDrawerMenu.PICKUP, 0);
    backing.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 60));
    icon.set(DataComponents.CUSTOM_NAME, Component.literal("Live container update"));
    drawer.action(player, live);
    require(
        menu.getCarried().getCount() == 60 && backing.getStackInSlot(0).isEmpty(),
        "Live contents and icon updates do not starve requests; transfers use current contents");
    menu.setCarried(ItemStack.EMPTY);
    backing.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 60));
    var stale = request(drawer, menu, ContainerDrawerMenu.PICKUP, 0);
    send(drawer, menu, player, ContainerDrawerMenu.PAGE, 27);
    drawer.action(
        player,
        new ContainerDrawerNetwork.Request(
            menu.containerId,
            menu.widgetSession(),
            stale.revision(),
            drawer.state().acknowledged() + 1,
            ContainerDrawerMenu.PICKUP,
            54,
            27,
            ItemStack.EMPTY));
    require(menu.getCarried().isEmpty(), "Old view revision cannot target a newly selected page");
    send(drawer, menu, player, ContainerDrawerMenu.PAGE, 0);
    var wrongCursor = request(drawer, menu, ContainerDrawerMenu.PICKUP, 0);
    menu.setCarried(new ItemStack(Items.DIAMOND));
    drawer.action(player, wrongCursor);
    require(backing.getStackInSlot(0).getCount() == 60, "Cursor mismatch refused");
    menu.setCarried(ItemStack.EMPTY);
    player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 10));
    send(drawer, menu, player, ContainerDrawerMenu.QUICK_STORE, 0);
    require(
        player.getInventory().getItem(0).isEmpty()
            && backing.getStackInSlot(0).getCount() == 64
            && backing.getStackInSlot(2).getCount() == 15,
        "Shift store merges before empty slots");
    for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
    send(drawer, menu, player, ContainerDrawerMenu.QUICK_TAKE, 0);
    require(
        backing.getStackInSlot(0).getCount() == 64 && player.getInventory().getItem(40).isEmpty(),
        "Full inventory does not spill into equipment");
    player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 61));
    var transfer = request(drawer, menu, ContainerDrawerMenu.QUICK_TAKE, 0);
    drawer.action(player, transfer);
    drawer.action(player, transfer);
    require(
        backing.getStackInSlot(0).getCount() == 61
            && player.getInventory().getItem(0).getCount() == 64,
        "Partial extraction and duplicate request conserve items");
    backing.setStackInSlot(60, new ItemStack(Items.IRON_INGOT, 2));
    send(drawer, menu, player, ContainerDrawerMenu.PICKUP, 120);
    require(menu.getCarried().isEmpty(), "Off-page slot refused");
    send(drawer, menu, player, ContainerDrawerMenu.PAGE, 54);
    send(drawer, menu, player, ContainerDrawerMenu.PICKUP, 120);
    require(menu.getCarried().getCount() == 2, "Paged slot is accessible");
    var old = request(drawer, menu, ContainerDrawerMenu.PICKUP, 120);
    sources.set(0, new EquippedContainers.Source("back:0", icon.copy(), handler));
    drawer.action(player, old);
    require(
        !drawer.state().open() && menu.getCarried().getCount() == 2,
        "Replacing identical item closes drawer");
    sources.add(
        new EquippedContainers.Source(
            "back:1", new ItemStack(Items.CHEST), new ItemStackHandler(1)));
    drawer.refresh();
    send(drawer, menu, player, ContainerDrawerMenu.TOGGLE, 0);
    send(drawer, menu, player, ContainerDrawerMenu.SELECT, 1);
    require(
        drawer.state().selected() == 1 && drawer.state().contents().size() == 1,
        "Select another equipped container");
    var buf =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), level.registryAccess());
    try {
      ContainerDrawerNetwork.State.CODEC.encode(buf, drawer.state());
      var decoded = ContainerDrawerNetwork.State.CODEC.decode(buf);
      require(
          decoded.selected() == 1
              && decoded.keys().get(1).equals("back:1")
              && ItemStack.matches(decoded.icons().getFirst(), icon),
          "Snapshot codec retains item components");
      var receiver = new ContainerDrawerMenu(menu, player, () -> sources);
      receiver.receive(decoded);
      require(
          receiver.request(ContainerDrawerMenu.SYNC, 0, 27).orElseThrow().request()
              > decoded.acknowledged(),
          "Snapshot acknowledgments advance the next request sequence");
      require(!receiver.pendingInventory(), "Sync does not block vanilla inventory");
      var acknowledgement =
          new ContainerDrawerNetwork.State(
              decoded.menu(),
              decoded.equipment(),
              decoded.session(),
              decoded.revision(),
              decoded.acknowledged() + 1,
              decoded.open(),
              decoded.selected(),
              decoded.offset(),
              decoded.pageSize(),
              decoded.icons(),
              decoded.keys(),
              decoded.sizes(),
              decoded.contents(),
              decoded.descriptions());
      receiver.receive(acknowledgement);
      receiver.request(ContainerDrawerMenu.PICKUP, 0, 27).orElseThrow();
      require(receiver.pendingInventory(), "Item transfers wait for authoritative cursor updates");
      menu.curiosDrawer.receive(decoded);
      require(
          menu.curiosDrawer.state().equipment() && menu.curiosDrawer.state().icons().isEmpty(),
          "Backpack snapshots cannot overwrite the equipment channel");
    } finally {
      buf.release();
    }
    sources.clear();
    drawer.refresh();
    require(
        !drawer.state().open() && drawer.state().icons().isEmpty(), "Unequipping removes drawer");
    ContainerInteractionSmoke.check(menu, player);
    player.containerMenu = player.inventoryMenu;
    menu.setCarried(ItemStack.EMPTY);
    player.getInventory().clearContent();
  }

  private static ContainerDrawerNetwork.Request request(
      ContainerDrawerMenu drawer, MachineMenu menu, int kind, int arg) {
    return new ContainerDrawerNetwork.Request(
        menu.containerId,
        menu.widgetSession(),
        drawer.state().revision(),
        drawer.state().acknowledged() + 1,
        kind,
        arg,
        27,
        menu.getCarried().copy());
  }

  private static void send(
      ContainerDrawerMenu drawer, MachineMenu menu, Player player, int kind, int arg) {
    drawer.refresh();
    drawer.action(player, request(drawer, menu, kind, arg));
  }

  private static void require(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }
}
