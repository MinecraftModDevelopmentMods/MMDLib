package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function run when a block is placed, stepped on or randomly ticked.
 *
 * @docParam this (context) => { println(context.x); }
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/events/BlockEventHandler")
@ZenCodeType.Name("mods.mmdlib.BlockEventHandler")
@FunctionalInterface
public interface ScriptBlockEventHandler {
    /**
     * Runs when the block is placed, stepped on or randomly ticked.
     *
     * @param context the block's position and facing, and the entity if any
     * @docParam context context
     */
    @ZenCodeType.Method
    void handle(ScriptBlockEventContext context);
}
