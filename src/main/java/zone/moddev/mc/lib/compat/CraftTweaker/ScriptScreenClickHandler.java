package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function run when a screen element naming its handler ID in
 * {@code on-click} (or a variant) is clicked; runs on the server.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.ScreenClickHandler")
@FunctionalInterface
public interface ScriptScreenClickHandler {
    @ZenCodeType.Method
    void handle(ScriptScreenClickContext click);
}
