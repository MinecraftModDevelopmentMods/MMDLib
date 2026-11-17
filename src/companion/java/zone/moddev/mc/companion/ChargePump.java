package zone.moddev.mc.companion;

import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;

/** Example charging alongside recipe lanes: one mB water supplies 100 FE. */
public final class ChargePump extends StoredProcessor {
    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine host) {
        return host.resources().fluids("coolant").free(0) > 0;
    }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine host,
        int ticks) {
        var resources = host.resources();
        try (var tx = resources.begin()) {
            if (resources.energy("energy").insert(100, tx) != 100)
                return ProcessState.BLOCKED;
            if (resources.fluids("coolant").extract(0, 1, tx) != 1)
                return ProcessState.IDLE;
            tx.commit();
        }
        return ProcessState.RUNNING;
    }
}
