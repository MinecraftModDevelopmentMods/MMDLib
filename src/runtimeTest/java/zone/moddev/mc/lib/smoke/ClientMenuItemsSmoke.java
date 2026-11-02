package zone.moddev.mc.lib.smoke;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.gui.runtime.ClientMenuItems;

/**
 * Checks the client's slot handler against a real storage's rules. The GUI smoke test checks it
 * behind a real client menu.
 */
final class ClientMenuItemsSmoke {
  private ClientMenuItemsSmoke() {}

  static void check() {
    var changes = new int[1];
    var rules =
        new ItemStorage(2)
            .onChange(() -> changes[0]++)
            .role(Role.OUTPUT, 1)
            .filter(stack -> !stack.is(Items.DIRT), 0)
            .limit(16, 0);
    var client = new ClientMenuItems(3, rules);
    var iron = new ItemStack(Items.IRON_INGOT);

    require(client.isItemValid(0, iron), "the filter accepts iron");
    require(!client.isItemValid(0, new ItemStack(Items.DIRT)), "the filter refuses dirt");
    require(!client.isItemValid(1, iron), "an output slot refuses players");
    require(client.getSlotLimit(0) == 16, "the slot limit, got " + client.getSlotLimit(0));
    require(client.getSlotLimit(1) == rules.getSlotLimit(1), "an unlimited slot");

    // Past the storage's end, as ghost slots can be: refused, not thrown.
    require(!client.isItemValid(2, iron), "no rules for a slot past the storage");
    require(client.getSlotLimit(2) == 0, "no room in a slot past the storage");

    // Contents are the menu's: the server may put anything anywhere, and rules stay untouched.
    client.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 3));
    require(client.getStackInSlot(1).is(Items.GOLD_INGOT), "sync fills an output slot");
    require(rules.getStackInSlot(1).isEmpty(), "the rules' storage is left alone");
    require(changes[0] == 0, "the rules' storage saw no change");
  }

  private static void require(boolean condition, String what) {
    if (!condition) throw new AssertionError("Client menu items: " + what);
  }
}
