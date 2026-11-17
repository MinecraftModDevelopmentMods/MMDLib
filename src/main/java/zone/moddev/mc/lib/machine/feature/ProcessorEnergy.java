package zone.moddev.mc.lib.machine.feature;

import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.IoMode;

/** Supplies the machine's single authoritative energy store. */
public interface ProcessorEnergy {
    /**
     * Called once per live machine, before resource binding. Do not access host
     * resources here. The returned store stays bound for the machine's lifetime
     * and is saved by the host. Custom EnergyBuffer implementations must honor
     * checkpoint/consume and serialization.
     */
    EnergyBuffer createEnergyStorage(long capacity, long inputRate,
        long outputRate);

    /**
     * Live additional restrictions, intersected with the machine's side
     * settings. Null is unsided access. Returning DISABLED also affects already
     * cached capability views.
     */
    default IoMode energyMode(@Nullable Direction side) {
        return IoMode.BOTH;
    }
}
