package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.api.MaterialEventContexts.MachineCycle;

/**
 * A machine cycle starting or finishing, as a {@code MachineCycleHandler} sees
 * it.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.MachineCycleContext")
public final class ScriptMachineCycleContext {
    private final MachineCycle cycle;

    ScriptMachineCycleContext(MachineCycle cycle) {
        this.cycle = cycle;
    }

    /** The machine's block entity. */
    @ZenCodeType.Getter("machine")
    public BlockEntity getMachine() {
        return cycle.machine();
    }

    /** The machine's level. */
    @ZenCodeType.Getter("level")
    public Level getLevel() {
        return cycle.level();
    }

    /** The machine's position. */
    @ZenCodeType.Getter("pos")
    public BlockPos getPos() {
        return cycle.position();
    }

    /** The machine definition's ID, such as {@code example:crusher}. */
    @ZenCodeType.Getter("machineId")
    public String getMachineId() {
        return cycle.machineId().toString();
    }

    /** The cycle's recipe ID. */
    @ZenCodeType.Getter("recipe")
    public String getRecipe() {
        return cycle.recipe().toString();
    }

    /** The cycle's length, in ticks. */
    @ZenCodeType.Getter("time")
    public int getTime() {
        return cycle.time();
    }
}
