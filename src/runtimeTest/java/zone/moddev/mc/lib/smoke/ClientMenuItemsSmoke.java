package zone.moddev.mc.lib.smoke;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.gui.runtime.ClientMenuItems;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/**
 * Checks the client's slot handler against a real storage's rules. The GUI smoke test checks it
 * behind a real client menu.
 */
final class ClientMenuItemsSmoke {
    private ClientMenuItemsSmoke() {
    }

    static void check(RegistryAccess registries) {
        var changes = new int[1];
        var rules = new ItemStorage(2)
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

        plans();
        ReservationSyncSmoke.check(registries);
    }

    /**
     * The client answers every probe the way a real storage with the same contents and plan does.
     */
    private static void plans() {
        var none = ItemStack.EMPTY;
        matches("no plan", 16, iron(50), SlotPlan.NONE);
        matches("take one", 64, iron(50), new SlotPlan(1, none));
        matches("take all", 64, iron(5), new SlotPlan(5, none));
        matches("jam", 64, iron(1), new SlotPlan(1, gold(1)));
        matches("top slot", 64, iron(63), new SlotPlan(1, iron(2)));
        matches("empty plan", 64, iron(63), new SlotPlan(0, none));
        matches("empty slot", 64, none, new SlotPlan(0, gold(1)));
        matches("last one", 64, iron(1), new SlotPlan(1, none));
        matches("partial stack", 64, iron(2), new SlotPlan(1, gold(1)));
        matches("limited", 16, iron(10), new SlotPlan(1, none));

        var rules = new ItemStorage(1);
        var client = new ClientMenuItems(1, rules);
        client.setStackInSlot(0, iron(10));
        client.setPlan(0, new SlotPlan(1, none));
        require(client.hasPlan(0) && client.freeToTake(0) == 9, "a plan takes hold");
        client.setPlan(0, SlotPlan.NONE);
        require(!client.hasPlan(0), "NONE clears the plan");
        client.setPlan(1, new SlotPlan(1, none));
        client.setPlan(-1, new SlotPlan(1, none));
        require(!client.hasPlan(1) && !client.hasPlan(-1),
            "a slot that does not exist has no plan");

        // A ghost slot past the storage's end refuses a plan without throwing.
        var wide = new ClientMenuItems(3, new ItemStorage(2));
        wide.setPlan(2, new SlotPlan(1, none));
        require(!wide.hasPlan(2), "a slot past the storage takes no plan");
        wide.setPlan(1, new SlotPlan(1, none));
        require(wide.hasPlan(1), "a slot in the storage takes one");
    }

    private static void matches(String row, int limit, ItemStack held, SlotPlan plan) {
        var server = new ItemStorage(1).limit(limit, 0);
        server.setStackInSlot(0, held.copy());
        server.setPlan(0, plan);
        var client = new ClientMenuItems(1, new ItemStorage(1).limit(limit, 0));
        client.setStackInSlot(0, held.copy());
        client.setPlan(0, plan);
        require(client.hasPlan(0) == server.hasPlan(0), row + ": hasPlan");
        require(client.freeToTake(0) == server.freeToTake(0), row + ": freeToTake");
        for (var probe : new ItemStack[]{iron(1), iron(5), iron(30), gold(1), gold(64)}) {
            var what = row + " with " + probe.getCount() + " " + probe.getItem();
            require(client.isItemValid(0, probe) == server.isItemValid(0, probe), what + ": valid");
            require(client.maxHeld(0, probe) == server.maxHeld(0, probe), what + ": maxHeld");
            require(
                client.insertItem(0, probe, true).getCount() == server.insertItem(0, probe, true)
                    .getCount(),
                what + ": insert");
        }
        for (int amount : new int[]{1, 5, 64})
            require(
                client.extractItem(0, amount, true).getCount() == server
                    .extractItem(0, amount, true).getCount(),
                row + ": extract " + amount);
    }

    private static ItemStack iron(int count) {
        return new ItemStack(Items.IRON_INGOT, count);
    }

    private static ItemStack gold(int count) {
        return new ItemStack(Items.GOLD_INGOT, count);
    }

    private static void require(boolean condition, String what) {
        if (!condition)
            throw new AssertionError("Client menu items: " + what);
    }
}
