package zone.moddev.mc.lib.common.runtime;

import zone.moddev.mc.lib.common.types.MMDMaterial;


import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

public final class ScytheItem extends DiggerItem implements MaterialBacked {
    /**
     * The nested calls use the ordinary server break path so every affected
     * block still fires NeoForge's protection event and receives vanilla drops.
     * The guard lets those nested calls complete without recursively starting
     * another 3x3 harvest.
     */
    private static final ThreadLocal<Boolean> HARVESTING = ThreadLocal.withInitial(() -> false);
    private final MMDMaterial material;

    public ScytheItem(MMDMaterial material, MaterialToolTier tier, Properties properties) {
        super(tier, RuntimeTags.SCYTHE_HARVESTABLE,
                properties.attributes(DiggerItem.createAttributes(tier, 0.0F, 0.0F)));
        this.material = material;
    }

    @Override public MMDMaterial material() { return material; }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state,
            BlockPos position, LivingEntity miner) {
        boolean result = super.mineBlock(stack, level, state, position, miner);
        if (level.isClientSide || !(miner instanceof ServerPlayer player)
                || HARVESTING.get() || !state.is(RuntimeTags.SCYTHE_HARVESTABLE) || stack.isEmpty()) {
            return result;
        }

        HARVESTING.set(true);
        try {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    // The ordinary break already handled the center block.
                    if (dx == 0 && dz == 0) continue;
                    if (stack.isEmpty()) return result;
                    BlockPos target = position.offset(dx, 0, dz);
                    if (level.getBlockState(target).is(RuntimeTags.SCYTHE_HARVESTABLE)) {
                        player.gameMode.destroyBlock(target);
                    }
                }
            }
        } finally {
            HARVESTING.remove();
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        // Material-specific lore/effects belong to the callback/type implementation.
    }
}
