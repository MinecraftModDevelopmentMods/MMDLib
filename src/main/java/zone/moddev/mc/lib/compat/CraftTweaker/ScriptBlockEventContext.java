package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.world.entity.Entity;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.api.MaterialEventContexts.BlockAction;

/**
 * What {@code onPlaced}, {@code onSteppedOn} and {@code onRandomTick} handlers
 * receive: the block, and the entity that placed it or stepped on it.
 *
 * @docParam this context
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/events/BlockEventContext")
@ZenCodeType.Name("mods.mmdlib.BlockEventContext")
public final class ScriptBlockEventContext extends ScriptBlockContext {
    private final BlockAction action;

    ScriptBlockEventContext(BlockAction action) {
        super(action.level(), action.position());
        this.action = action;
    }

    /**
     * The entity that placed the block or stepped on it; null for random ticks.
     *
     * @return the entity, or null
     */
    @ZenCodeType.Nullable
    @ZenCodeType.Getter("entity")
    public Entity getEntity() {
        return action.entity();
    }
}
