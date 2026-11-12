package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function run when a block is placed, stepped on or randomly ticked.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.BlockEventHandler")
@FunctionalInterface
public interface ScriptBlockEventHandler {
    @ZenCodeType.Method
    void handle(ScriptBlockEventContext context);
}
