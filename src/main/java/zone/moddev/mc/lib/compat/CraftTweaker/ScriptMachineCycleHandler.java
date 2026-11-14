package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function run when an XML machine whose processor names its handler
 * ID in {@code on-start} or {@code on-finish} starts or finishes a cycle; runs
 * on the server.
 *
 * @docParam this (cycle) => { println(cycle.recipe); }
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/machines/MachineCycleHandler")
@ZenCodeType.Name("mods.mmdlib.MachineCycleHandler")
@FunctionalInterface
public interface ScriptMachineCycleHandler {
    /**
     * Runs when the machine starts or finishes a cycle, on the server.
     *
     * @param cycle the cycle: machine, level, position, machine ID, recipe and
     *            length
     * @docParam cycle cycle
     */
    @ZenCodeType.Method
    void handle(ScriptMachineCycleContext cycle);
}
