package zone.moddev.mc.companion;

import net.minecraft.core.Direction;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.machine.feature.ProcessorEnergy;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;

/**
 * Processor-owned energy, with side policy applied to cached capability views.
 */
public final class BatteryBank extends StoredProcessor
    implements ProcessorEnergy {
    private EnergyBuffer buffer;
    public EnergyBuffer createEnergyStorage(long capacity, long input,
        long output) {
        if (buffer != null)
            throw new IllegalStateException("Already bound");
        return buffer = new EnergyBuffer(capacity, input, output);
    }
    public EnergyBuffer buffer() {
        return buffer;
    }
    public IoMode energyMode(Direction side) {
        if (paused())
            return IoMode.DISABLED;
        return side == null
            ? IoMode.BOTH
            : side == Direction.UP
                ? IoMode.INPUT
                : side == Direction.DOWN ? IoMode.OUTPUT : IoMode.DISABLED;
    }
    public boolean canStart(ProcessorState state, ProcessorMachine machine) {
        return false;
    }
    public ProcessState process(ProcessorState state, ProcessorMachine machine,
        int ticks) {
        return ProcessState.IDLE;
    }
}
