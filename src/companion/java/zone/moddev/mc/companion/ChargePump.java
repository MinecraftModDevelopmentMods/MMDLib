package zone.moddev.mc.companion;

import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;

/** Example charging alongside recipe lanes: one mB water supplies 100 FE. */
public final class ChargePump extends StoredProcessor {
    private final ProcessorState.Key<Integer> starts = variables.define(
        "starts", com.mojang.serialization.Codec.INT, 0,
        java.util.function.UnaryOperator.identity(), false, true);
    private final ProcessorState.Key<Integer> finishes = variables.define(
        "finishes", com.mojang.serialization.Codec.INT, 0,
        java.util.function.UnaryOperator.identity(), false, true);
    private final ProcessorState.Key<String> lastLane = variables.define(
        "last_lane", com.mojang.serialization.Codec.STRING, "",
        java.util.function.UnaryOperator.identity(), false, true);

    public void recordCycle(String lane, boolean finished) {
        variables.update(() -> {
            var counter = finished ? finishes : starts;
            variables.set(counter, variables.get(counter) + 1);
            variables.set(lastLane, lane);
        });
    }

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
