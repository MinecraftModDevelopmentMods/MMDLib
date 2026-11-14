package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function run when a screen element naming its handler ID in
 * {@code on-click} (or a variant) is clicked; runs on the server.
 *
 * @docParam this (click) => { println(click.element); }
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/screens/ScreenClickHandler")
@ZenCodeType.Name("mods.mmdlib.ScreenClickHandler")
@FunctionalInterface
public interface ScriptScreenClickHandler {
    /**
     * Runs when the element is clicked, on the server.
     *
     * @param click the click: player, block entity, screen, element, button,
     *            keys and option
     * @docParam click click
     */
    @ZenCodeType.Method
    void handle(ScriptScreenClickContext click);
}
