package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import org.openzen.zencode.java.ZenCodeType;

/** A script function run when a block is about to be broken. */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.BreakHandler")
@FunctionalInterface
public interface ScriptBreakHandler {
  @ZenCodeType.Method
  void handle(ScriptBreakContext context);
}
