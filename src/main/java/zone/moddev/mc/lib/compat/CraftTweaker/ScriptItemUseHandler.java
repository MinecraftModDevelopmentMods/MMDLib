package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function run during the use of an item: start, each tick, stop or
 * finish.
 *
 * @docParam this (context) => { println(context.phase); }
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/events/ItemUseHandler")
@ZenCodeType.Name("mods.mmdlib.ItemUseHandler")
@FunctionalInterface
public interface ScriptItemUseHandler {
    /**
     * Runs at one stage of using the item: start, each tick, stop or finish.
     *
     * @param context the use: entity, item, phase, duration and result
     * @docParam context context
     */
    @ZenCodeType.Method
    void handle(ScriptItemUseContext context);
}
