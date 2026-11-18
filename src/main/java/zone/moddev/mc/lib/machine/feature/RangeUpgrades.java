package zone.moddev.mc.lib.machine.feature;

import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.common.item.RangeUpgradeItem;

/**
 * Shared range policy: one radius increment per occupied socket, at most nine.
 */
public final class RangeUpgrades {
    public static final int MAX_UPGRADES = 9;
    private RangeUpgrades() {
    }

    public static int count(Iterable<ItemStack> sockets) {
        int count = 0;
        for (var stack : sockets) {
            if (!stack.isEmpty() && stack.getItem() instanceof RangeUpgradeItem
                && ++count == MAX_UPGRADES)
                break;
        }
        return count;
    }

    public static WorkingArea apply(WorkingArea base, int count) {
        return base == null
            ? null
            : base.expandHorizontal(Math.clamp(count, 0, MAX_UPGRADES));
    }
}
