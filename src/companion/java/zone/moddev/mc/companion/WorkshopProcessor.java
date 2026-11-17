package zone.moddev.mc.companion;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;

/** A small transactional consumer of typed host resources; no entity cast. */
public final class WorkshopProcessor extends StoredProcessor {
    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine host) {
        var resources = host.resources();
        return resources.items("input").peek(0).is(Items.COBBLESTONE)
            && resources.items("input").free(0) > 0
            && resources.fluids("water").peek(0).fluid().value() == Fluids.WATER
            && resources.fluids("water").free(0) >= 25;
    }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine host,
        int ticks) {
        var resources = host.resources();
        for (int step = 0; step < ticks; step++) {
            if (!canStart(state, host))
                return ProcessState.IDLE;
            try (var tx = resources.begin()) {
                if (resources.items("input").extract(0, 1, tx) != 1
                    || resources.fluids("water").extract(0, 25, tx) != 25
                    || resources.items("output")
                        .insert(new ItemStack(Items.STONE), tx) != 1)
                    return ProcessState.BLOCKED;
                tx.commit();
            }
        }
        return ProcessState.RUNNING;
    }
}
