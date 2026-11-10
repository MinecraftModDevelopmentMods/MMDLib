package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import org.openzen.zencode.java.ZenCodeType;

/** A script function run when a player right-clicks with an item. */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.ItemClickHandler")
@FunctionalInterface
public interface ScriptItemClickHandler {
    @ZenCodeType.Method
    void handle(ScriptItemClickContext context);
}
