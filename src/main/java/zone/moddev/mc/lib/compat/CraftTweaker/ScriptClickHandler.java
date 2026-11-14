package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function run when a player right-clicks a block.
 *
 * @docParam this (context) => { println(context.face); }
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/events/ClickHandler")
@ZenCodeType.Name("mods.mmdlib.ClickHandler")
@FunctionalInterface
public interface ScriptClickHandler {
    /**
     * Runs when a player right-clicks the block.
     *
     * @param context the click: player, sneaking, face and the block's position
     * @docParam context context
     */
    @ZenCodeType.Method
    void handle(ScriptClickContext context);
}
