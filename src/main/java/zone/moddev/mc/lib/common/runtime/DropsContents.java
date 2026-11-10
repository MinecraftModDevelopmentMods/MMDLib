package zone.moddev.mc.lib.common.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Implement on a block entity of a {@link MaterialEntityBlock} to drop what it
 * holds when its block is broken or replaced by another block. A change of
 * state of the same block does not call it.
 */
public interface DropsContents {
    /**
     * Drops the contents at {@code pos}; the block entity is removed right
     * after.
     */
    void dropContents(Level level, BlockPos pos);

    /**
     * Shared block-removal hook; call before the superclass removes the block
     * entity.
     */
    static void onRemove(BlockState state, Level level, BlockPos pos,
        BlockState replacement) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide()
            && level.getBlockEntity(pos) instanceof DropsContents contents)
            contents.dropContents(level, pos);
    }
}
