package zone.moddev.mc.lib.machine.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.machine.neoforge.MachineVisualState;

class MachineVisualStateTest {
    @Test
    void trackingSnapshotsOnlyIncludeReferencedValuesAndRebuildOnConditionTransitions() {
        var fill = new AtomicInteger();
        var variables = new VisualVariables.Builder()
            .add("tank.fill", 0.0, () -> fill.get() / 100.0)
            .add("unreferenced", "private", () -> "contents")
            .build();
        var server = new MachineVisualState(variables, List.of("$tank.fill >= 0.5"));
        var client = new MachineVisualState(variables, List.of("$tank.fill >= 0.5"));
        var initial = server.write();
        assertEquals(Set.of("tank.fill"), initial.getAllKeys());
        assertFalse(client.apply(initial));
        fill.set(60);
        variables.changed("tank.fill");
        assertTrue(client.apply(server.write()));
        var prior = client.modelData().get(MachineVisualState.VARIABLES);
        fill.set(80);
        variables.changed("tank.fill");
        assertFalse(client.apply(server.write()));
        assertEquals(0.6, prior.get("tank.fill"));
        assertEquals(0.8, client.modelData().get(MachineVisualState.VARIABLES).get("tank.fill"));
        // A newly tracking client receives current state without needing an open menu.
        var joining = new MachineVisualState(variables, List.of("$tank.fill >= 0.5"));
        assertTrue(joining.apply(server.write()));
        fill.set(0);
        variables.changed("tank.fill");
        assertTrue(client.apply(server.write()));
    }

    @Test
    void notificationsScheduleOneUpdateAndCleanSnapshotsNeverPollGetters() {
        var changed = new zone.moddev.mc.lib.api.util.ChangeSignal();
        var unrelated = new zone.moddev.mc.lib.api.util.ChangeSignal();
        var value = new AtomicInteger();
        var reads = new AtomicInteger();
        var schedules = new AtomicInteger();
        var broadcasts = new AtomicInteger();
        var variables = new VisualVariables.Builder().observedBy(changed)
            .add("value", 0.0, () -> {
                reads.incrementAndGet();
                return value.get();
            })
            .observedBy(unrelated)
            .add("unused", false, () -> {
                throw new AssertionError("Unreferenced getter");
            })
            .build();
        var state = new MachineVisualState(variables, List.of("$value > 0"));
        state.start(schedules::incrementAndGet);
        state.flush(broadcasts::incrementAndGet);
        assertEquals(1, reads.get());
        assertEquals(1, schedules.get());
        for (int i = 0; i < 100; i++)
            state.write();
        unrelated.changed();
        assertEquals(1, reads.get(), "Initial snapshots and unrelated changes use cached values");
        assertEquals(1, schedules.get());
        value.set(1);
        changed.changed();
        changed.changed();
        assertEquals(2, schedules.get(), "Mutations coalesce into one pending update");
        state.write(); // A newly tracking player must not consume the pending broadcast.
        state.flush(broadcasts::incrementAndGet);
        assertEquals(2, broadcasts.get());
        assertEquals(2, reads.get());
        changed.changed();
        state.flush(broadcasts::incrementAndGet);
        assertEquals(2, broadcasts.get(), "A notification without a value change sends nothing");
        state.stop();
        value.set(0);
        changed.changed();
        assertEquals(3, schedules.get(), "Unloaded owners have no listeners");
        state.start(schedules::incrementAndGet);
        state.flush(broadcasts::incrementAndGet);
        assertEquals(3, broadcasts.get(), "Reload samples current values and reconnects listeners");
    }

    @Test
    void energyCapacityAndRestorationInvalidateSnapshotsWithoutReplacingPersistenceCallback() {
        var persisted = new AtomicInteger();
        var energy = new zone.moddev.mc.lib.api.capabilities.EnergyBuffer(100, 100)
            .onChange(persisted::incrementAndGet);
        var variables = new VisualVariables.Builder()
            .component("energy", BuiltinVisualVariables.energy(energy)).build();
        var state = new MachineVisualState(variables, List.of("$energy.fill >= 0.5"));
        assertEquals(0.0, state.write().getDouble("energy.fill"));
        energy.onChange(() -> {
            persisted.incrementAndGet();
            assertEquals((double) energy.stored() / energy.capacity(),
                state.write().getDouble("energy.fill"),
                "Persistence callbacks can send a current snapshot immediately");
        });
        energy.setStored(50);
        assertEquals(0.5, state.write().getDouble("energy.fill"));
        assertEquals(1, persisted.get());
        energy.setCapacity(200);
        assertEquals(0.25, state.write().getDouble("energy.fill"));
        energy.deserializeNBT(null, net.minecraft.nbt.LongTag.valueOf(100));
        assertEquals(0.5, state.write().getDouble("energy.fill"));
    }

    @Test
    void malformedAndMissingWireValuesResetToDefaultsWithoutRetainingOldState() {
        var variables = new VisualVariables.Builder().add("running", false, () -> true).build();
        var server = new MachineVisualState(variables, List.of("$running"));
        var client = new MachineVisualState(variables, List.of("$running"));
        assertTrue(client.apply(server.write()));
        var malformed = new CompoundTag();
        malformed.putString("running", "true");
        assertTrue(client.apply(malformed));
        assertEquals(false, client.modelData().get(MachineVisualState.VARIABLES).get("running"));
        assertFalse(client.apply(new CompoundTag()));
    }
}
