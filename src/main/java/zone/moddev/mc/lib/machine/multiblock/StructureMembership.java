package zone.moddev.mc.lib.machine.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Ownership seam for future passive casing indexes; phase 1 uses linked block
 * entities.
 */
public interface StructureMembership {
    MemberLink link(Level level, BlockPos pos);
    boolean claim(Level level, BlockPos pos, MemberLink link);
    void release(Level level, BlockPos pos, MemberLink expected);
    default void availabilityChanged(Level level, BlockPos pos) {
    }
}
