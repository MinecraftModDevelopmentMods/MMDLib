package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function run when the mouse enters or leaves a screen element naming
 * its handler ID in {@code on-hover}; runs on the client, for display only.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.ScreenHoverHandler")
@FunctionalInterface
public interface ScriptScreenHoverHandler {
    @ZenCodeType.Method
    void handle(ScriptScreenHoverContext hover);
}
