package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import org.slf4j.Logger;

import zone.moddev.mc.lib.api.MaterialEvent;
import zone.moddev.mc.lib.api.MaterialEventContexts.MachineCycle;
import zone.moddev.mc.lib.api.MaterialEventTypes;

/**
 * Registers script functions as XML machine cycle handlers, under IDs that a
 * machine's {@code <processor>} names in {@code on-start} and {@code
 * on-finish}.
 *
 * <p>
 * The machine stays XML; the script only supplies what a handler ID does. IDs
 * are {@code mmdlib:<name>}. Machines load after scripts run, and each
 * machine's handler IDs are checked then: one naming an ID no script or mod
 * registered, or a start handler used for finishing, fails loading. A function
 * that throws is logged, and the machine carries on. Only recipe processors
 * have cycles; a processor naming a Java {@code class} cannot take these
 * attributes.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/machines/MachineHandlers")
@ZenCodeType.Name("mods.mmdlib.MachineHandlers")
public final class ScriptMachineHandlers {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ScriptMachineHandlers() {
    }

    /**
     * Registers {@code handler} as the cycle-start handler {@code
     * mmdlib:<name>}, run when a machine starts a new cycle (not when a paused
     * one resumes). It is an error if the ID is already a handler.
     *
     * @param name the name; the ID is {@code mmdlib:<name>}
     * @param handler runs as each cycle starts
     * @return the handler's ID, to write in machine XML
     * @docParam name "crusher_start"
     * @docParam handler (cycle) => { println(cycle.recipe); }
     */
    @ZenCodeType.Method
    public static String onStart(String name,
        ScriptMachineCycleHandler handler) {
        return register(name, MaterialEventTypes.MACHINE_CYCLE_START, handler);
    }

    /**
     * Registers {@code handler} as the cycle-finish handler {@code
     * mmdlib:<name>}, run when a machine finishes a cycle and has placed its
     * outputs. It is an error if the ID is already a handler.
     *
     * @param name the name; the ID is {@code mmdlib:<name>}
     * @param handler runs as each cycle finishes
     * @return the handler's ID, to write in machine XML
     * @docParam name "crusher_done"
     * @docParam handler (cycle) => { println(cycle.recipe); }
     */
    @ZenCodeType.Method
    public static String onFinish(String name,
        ScriptMachineCycleHandler handler) {
        return register(name, MaterialEventTypes.MACHINE_CYCLE_FINISH, handler);
    }

    /**
     * Whether a handler is registered, from a script or code.
     *
     * @param id the handler ID
     * @return true if registered
     * @docParam id "mmdlib:crusher_done"
     */
    @ZenCodeType.Method
    public static boolean exists(String id) {
        return ScriptDefinitions.catalog().events().callbacks()
            .containsKey(ResourceLocation.parse(id));
    }

    private static String register(String name,
        MaterialEvent<MachineCycle> event, ScriptMachineCycleHandler handler) {
        var id = ScriptDefinitionProperties.mmdlibId(name, "machine handler");
        ScriptDefinitions.catalog().events().registerCallback(id, event,
            cycle -> {
                try {
                    handler.handle(new ScriptMachineCycleContext(cycle));
                } catch (RuntimeException ex) {
                    LOGGER.error("Script machine handler {} failed", id, ex);
                }
            });
        return id.toString();
    }
}
