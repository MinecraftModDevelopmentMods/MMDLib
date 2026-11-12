package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import org.openzen.zencode.java.ZenCodeType;

/** A script function run when a player right-clicks a block. */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.ClickHandler")
@FunctionalInterface
public interface ScriptClickHandler {
    @ZenCodeType.Method
    void handle(ScriptClickContext context);
}
