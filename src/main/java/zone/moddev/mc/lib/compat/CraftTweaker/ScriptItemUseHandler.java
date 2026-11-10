package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function run during the use of an item: start, each tick, stop or
 * finish.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.ItemUseHandler")
@FunctionalInterface
public interface ScriptItemUseHandler {
    @ZenCodeType.Method
    void handle(ScriptItemUseContext context);
}
