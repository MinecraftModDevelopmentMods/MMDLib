package zone.moddev.mc.lib.machine.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Common-side world context, supplied by the host rather than a mod block
 * entity.
 */
public interface ProcessorMachine {
    Level level();
    BlockPos position();
    void changed();
    default void changed(boolean synchronize) {
        changed();
    }
    net.minecraft.world.level.block.state.BlockState blockState();
    net.minecraft.world.item.ItemStack item();
    /**
     * Installed range sockets shared by all area-providing processors on this
     * host.
     */
    default int rangeUpgrades() {
        return 0;
    }
    /**
     * Server-side snapshot of a named item inventory in local slot order.
     * Stacks are detached copies; changing them never changes machine storage.
     * Hosts without the requested inventory reject the name.
     */
    default java.util.List<net.minecraft.world.item.ItemStack> inventoryContents(
        String inventory) {
        throw new IllegalArgumentException("Unknown inventory: " + inventory);
    }
    /**
     * Authorize player pickup before ownership changes. Hosts must enforce
     * build permissions and platform protection events; absent support denies
     * it.
     */
    default boolean authorizePickup(
        net.minecraft.world.entity.player.Player player) {
        return false;
    }
    void remove();
    boolean removed();
}
