package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function run when a player right-clicks with an item.
 *
 * @docParam this (context) => { println(context.target); }
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/events/ItemClickHandler")
@ZenCodeType.Name("mods.mmdlib.ItemClickHandler")
@FunctionalInterface
public interface ScriptItemClickHandler {
    /**
     * Runs when a player right-clicks with the item.
     *
     * @param context the click: player, item, and what was clicked
     * @docParam context context
     */
    @ZenCodeType.Method
    void handle(ScriptItemClickContext context);
}
