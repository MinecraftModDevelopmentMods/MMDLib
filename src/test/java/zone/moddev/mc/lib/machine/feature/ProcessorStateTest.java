package zone.moddev.mc.lib.machine.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.machine.presentation.VisualVariables;

class ProcessorStateTest {
    @Test
    void changesAreBatchedAndUnrelatedVariablesDoNotInvalidateBindings() {
        var state = new ProcessorState();
        var occupied = state.define("occupied", Codec.BOOL, false);
        var cooldown = state.define("cooldown", Codec.INT, 0,
            java.util.function.UnaryOperator.identity(), false, false);
        var builder = new VisualVariables.Builder();
        state.defineVisualVariables(builder);
        var variables = builder.build();
        var notifications = new AtomicInteger();
        var saves = new AtomicInteger();
        var syncs = new AtomicInteger();
        variables.subscribe(Set.of("occupied"),
            ignored -> notifications.incrementAndGet());
        state.onChange(saves::incrementAndGet);
        state.onBatch(sync -> {
            if (sync)
                syncs.incrementAndGet();
        });
        assertThrows(IllegalStateException.class,
            () -> state.set(occupied, true));
        state.update(() -> {
            state.set(occupied, true);
            state.set(occupied, true);
            state.set(cooldown, 40);
            assertEquals(0, notifications.get());
        });
        assertEquals(1, notifications.get());
        assertEquals(1, saves.get());
        state.update(() -> state.set(cooldown, 39));
        assertEquals(1, notifications.get());
        state.update(() -> state.set(occupied, true));
        assertEquals(2, saves.get());
        assertEquals(1, syncs.get());
    }

    @Test
    void payloadsAreCopiedAndInvalidRestoreCannotPartiallyChangeState() {
        var state = new ProcessorState();
        var count = state.define("count", Codec.intRange(0, 40), 0);
        var data = state.define("mob", CompoundTag.CODEC, new CompoundTag(),
            CompoundTag::copy, false, true);
        var payload = new CompoundTag();
        payload.putString("name", "Bessie");
        state.update(() -> {
            state.set(count, 40);
            state.set(data, payload);
        });
        payload.putString("name", "Changed externally");
        state.get(data).putString("name", "Changed through getter");
        assertEquals("Bessie", state.get(data).getString("name"));
        var saved = state.save(false);
        state.update(() -> state.set(count, 0));
        state.load(saved, false);
        assertEquals(40, state.get(count));
        saved.putInt("count", -1);
        assertThrows(IllegalStateException.class,
            () -> state.load(saved, false));
        assertEquals(40, state.get(count));
        assertEquals("Bessie", state.get(data).getString("name"));
    }

    @Test
    void uiOnlyConditionsObserveInitialRestoreAndSubsequentChanges() {
        var processor = new Fixture();
        processor.initialize("courier", null);
        var host = new FeatureHost(() -> {
        }).add(processor);
        assertFalse(host.condition("$processors.courier.occupied"));
        var saved = processor.state();
        saved.putBoolean("occupied", true);
        processor.restore(saved);
        assertTrue(host.condition("$processors.courier.occupied"));
        assertTrue(host.condition(
            "$processors.courier.occupied && !$processors.courier.paused"));
        processor.setPaused(true);
        assertFalse(host.condition(
            "$processors.courier.occupied && !$processors.courier.paused"));
        assertTrue(host.condition("$processors.courier.occupied"));
        assertThrows(IllegalArgumentException.class,
            () -> host.condition("$processors.courier.missing"));
    }

    @Test
    void pausedAndHeldOperationsResumeWithoutStartingAgain() {
        var starts = new AtomicInteger();
        var steps = new AtomicInteger();
        var processor = new StoredProcessor() {
            public boolean canStart(ProcessorState state,
                ProcessorMachine machine) {
                starts.incrementAndGet();
                return true;
            }
            public ProcessState process(ProcessorState state,
                ProcessorMachine machine, int deltaTicks) {
                steps.addAndGet(deltaTicks);
                return ProcessState.RUNNING;
            }
        };
        processor.initialize("work", null);
        var host = new FeatureHost(() -> {
        }).add(processor);
        processor.tick(host);
        processor.setPaused(true);
        processor.tick(host);
        assertFalse(processor.processing());
        assertEquals(1, steps.get());
        processor.setPaused(false);
        processor.suspended(host);
        assertEquals(Processor.ProcessState.HELD, processor.executionStatus());
        assertFalse(processor.processing());
        processor.tick(host);
        assertTrue(processor.processing());
        assertEquals(1, starts.get());
        assertEquals(2, steps.get());
    }

    public static class Fixture extends StoredProcessor {
        final ProcessorState.Key<Boolean> occupied = variables
            .define("occupied", Codec.BOOL, false);
        public boolean canStart(ProcessorState state,
            ProcessorMachine context) {
            return true;
        }
        public ProcessState process(ProcessorState state,
            ProcessorMachine context, int deltaTicks) {
            return ProcessState.IDLE;
        }
    }
}
