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
