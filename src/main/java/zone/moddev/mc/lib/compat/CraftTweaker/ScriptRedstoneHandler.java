package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function run when the power reaching a block changes.
 *
 * @docParam this (context) => { context.output = context.power; }
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/events/RedstoneHandler")
@ZenCodeType.Name("mods.mmdlib.RedstoneHandler")
@FunctionalInterface
public interface ScriptRedstoneHandler {
    /**
     * Runs when the power reaching the block changes.
     *
     * @param context the block, the power reaching it and before, and its
     *            output
     * @docParam context context
     */
    @ZenCodeType.Method
    void handle(ScriptRedstoneContext context);
}
