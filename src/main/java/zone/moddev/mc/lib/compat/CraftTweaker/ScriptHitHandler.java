package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import org.openzen.zencode.java.ZenCodeType;

/** A script function run after a melee hit with an item. */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.HitHandler")
@FunctionalInterface
public interface ScriptHitHandler {
  @ZenCodeType.Method
  void handle(ScriptHitContext context);
}
