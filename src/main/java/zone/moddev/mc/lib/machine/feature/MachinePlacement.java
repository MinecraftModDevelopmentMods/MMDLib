package zone.moddev.mc.lib.machine.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/** Successful server placement, after portable state restoration. */
public record MachinePlacement(Level level, BlockPos position, BlockState state,
    @Nullable Player placer, ItemStack item) {
    public MachinePlacement {
        position = position.immutable();
        item = item.copy();
    }

    @Override
    public ItemStack item() {
        return item.copy();
    }
}
