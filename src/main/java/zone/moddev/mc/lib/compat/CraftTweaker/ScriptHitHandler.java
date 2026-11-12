package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function run after a melee hit with an item.
 *
 * @docParam this (context) => { println(context.target); }
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/events/HitHandler")
@ZenCodeType.Name("mods.mmdlib.HitHandler")
@FunctionalInterface
public interface ScriptHitHandler {
    /**
     * Runs after a melee hit with the item.
     *
     * @param context the hit: attacker, target, item and damage
     * @docParam context context
     */
    @ZenCodeType.Method
    void handle(ScriptHitContext context);
}
