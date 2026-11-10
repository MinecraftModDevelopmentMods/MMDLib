package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import org.openzen.zencode.java.ZenCodeType;

/** A script function run when the power reaching a block changes. */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.RedstoneHandler")
@FunctionalInterface
public interface ScriptRedstoneHandler {
    @ZenCodeType.Method
    void handle(ScriptRedstoneContext context);
}
