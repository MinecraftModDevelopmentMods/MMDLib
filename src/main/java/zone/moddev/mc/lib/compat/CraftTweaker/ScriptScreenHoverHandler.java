package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function run when the mouse enters or leaves a screen element naming
 * its handler ID in {@code on-hover}; runs on the client, for display only.
 *
 * @docParam this (hover) => { println(hover.entered); }
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/screens/ScreenHoverHandler")
@ZenCodeType.Name("mods.mmdlib.ScreenHoverHandler")
@FunctionalInterface
public interface ScriptScreenHoverHandler {
    /**
     * Runs when the mouse enters or leaves the element, on the client.
     *
     * @param hover the hover: element and whether the mouse entered
     * @docParam hover hover
     */
    @ZenCodeType.Method
    void handle(ScriptScreenHoverContext hover);
}
