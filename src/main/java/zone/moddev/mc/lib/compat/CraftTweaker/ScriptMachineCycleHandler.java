package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function run when an XML machine whose processor names its handler
 * ID in {@code on-start} or {@code on-finish} starts or finishes a cycle; runs
 * on the server.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.MachineCycleHandler")
@FunctionalInterface
public interface ScriptMachineCycleHandler {
    @ZenCodeType.Method
    void handle(ScriptMachineCycleContext cycle);
}
