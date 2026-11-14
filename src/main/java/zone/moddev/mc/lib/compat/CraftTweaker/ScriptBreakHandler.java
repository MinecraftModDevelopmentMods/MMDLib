package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function run when a block is about to be broken.
 *
 * @docParam this (context) => { context.cancel(); }
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/events/BreakHandler")
@ZenCodeType.Name("mods.mmdlib.BreakHandler")
@FunctionalInterface
public interface ScriptBreakHandler {
    /**
     * Runs when the block is about to be broken.
     *
     * @param context the break: player, tool, block, face, and a way to cancel
     *            it
     * @docParam context context
     */
    @ZenCodeType.Method
    void handle(ScriptBreakContext context);
}
