package zone.moddev.mc.lib.common.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import zone.moddev.mc.lib.api.MaterialEvent;
import zone.moddev.mc.lib.api.MaterialEventContexts.BlockAction;
import zone.moddev.mc.lib.api.MaterialEventTypes;
import zone.moddev.mc.lib.api.MaterialEvents;
import zone.moddev.mc.lib.common.types.MMDBlock;
import zone.moddev.mc.lib.common.types.MMDMaterial;

/**
 * The block events only a block's own class can fire, as NeoForge has no event
 * for them: {@code
 * block-stepped-on} and {@code block-random-tick}. MMDLib's plain block and
 * redstone block call these from their overrides.
 */
public record BlockHooks(MaterialEvents events, MMDBlock definition,
    MMDMaterial material) {
    /** An entity stepped on the block. */
    public void steppedOn(Level level, BlockPos pos, BlockState state,
        Entity entity) {
        fire(MaterialEventTypes.BLOCK_STEPPED_ON,
            new BlockAction(level, pos, state, entity));
    }

    /**
     * The block got a random tick; only blocks with random ticks on get them.
     */
    public void randomTick(ServerLevel level, BlockPos pos, BlockState state) {
        fire(MaterialEventTypes.BLOCK_RANDOM_TICK,
            new BlockAction(level, pos, state, null));
    }

    private void fire(MaterialEvent<BlockAction> event, BlockAction context) {
        var id = ItemEventBridge.binding(definition.events(), material,
            event.name());
        if (id != null)
            events.dispatch(id, event, context);
    }
}
