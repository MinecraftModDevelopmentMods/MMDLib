package zone.moddev.mc.lib.machine.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

class ProcessorClockTest {
    /**
     * Consumer processor deliberately unrelated to StoredProcessor or
     * RecipeProcessor.
     */
    private static final class Consumer implements Processor<Integer> {
        final String key;
        boolean paused;
        int ticks;
        int energy = 1000;
        ProcessState inactive;
        ProcessState status = ProcessState.IDLE;
        Consumer(String key) {
            this.key = key;
        }
        public String key() {
            return key;
        }
        public Codec<Integer> codec() {
            return Codec.INT;
        }
        public Integer state() {
            return ticks;
        }
        public void restore(Integer value) {
            ticks = value;
        }
        public boolean paused() {
            return paused;
        }
        public void setPaused(boolean value) {
            paused = value;
        }
        public boolean processing() {
            return status == ProcessState.RUNNING;
        }
        public ProcessState executionStatus() {
            return status;
        }
        public void defineVisualVariables(
            zone.moddev.mc.lib.machine.presentation.VisualVariables.Builder variables) {
        }
        public void tick(FeatureHost host) {
            if (inactive != null) {
                status = inactive;
                return;
            }
            if (paused) {
                status = ProcessState.PAUSED;
                return;
            }
            if (energy < 5) {
                status = ProcessState.STALLED;
                return;
            }
            ticks++;
            energy -= 5;
            status = ProcessState.RUNNING;
        }
    }

    @Test
    void everyStackSizeScalesAllProcessorsAndTheirNormalEnergyUse() {
        for (int count = 0; count <= 10; count++) {
            int installed = count;
            var clock = new ProcessorClock(() -> installed);
            var host = new FeatureHost(() -> {
            });
            var a = new Consumer("a");
            var b = new Consumer("b");
            for (int tick = 0; tick < 8; tick++) {
                clock.tick(a, host);
                clock.tick(b, host);
            }
            assertEquals(8 + Math.min(8, count), a.ticks);
            assertEquals(a.ticks, b.ticks);
            assertEquals(1000 - a.ticks * 5, a.energy);
            assertEquals(a.energy, b.energy);
        }
    }

    @Test
    void fractionalWorkPersistsAndPausedProcessorsDoNotBankWork() {
        var clock = new ProcessorClock(() -> 1);
        var host = new FeatureHost(() -> {
        }).add(clock);
        var processor = new Consumer("worker");
        for (int tick = 0; tick < 7; tick++)
            clock.tick(processor, host);
        processor.setPaused(true);
        for (int tick = 0; tick < 40; tick++)
            clock.tick(processor, host);
        assertEquals(7, processor.ticks);
        var restored = new ProcessorClock(() -> 1);
        new FeatureHost(() -> {
        }).add(restored).load(JsonOps.INSTANCE, host.save(JsonOps.INSTANCE));
        processor.setPaused(false);
        restored.tick(processor, host);
        assertEquals(9, processor.ticks);
    }

    @Test
    void inactiveProcessorsDoNotChangeSavedFractionsOrDirtyTheHost() {
        for (int installed = 1; installed <= 8; installed++) {
            int count = installed;
            var changes = new java.util.concurrent.atomic.AtomicInteger();
            var host = new FeatureHost(changes::incrementAndGet);
            var clock = new ProcessorClock(() -> count);
            var processor = new Consumer("worker");
            clock.tick(processor, host);
            var saved = clock.state();
            changes.set(0);
            for (var state : Processor.ProcessState.values()) {
                if (state == Processor.ProcessState.RUNNING)
                    continue;
                processor.inactive = state;
                for (int tick = 0; tick < 40; tick++)
                    clock.tick(processor, host);
                assertEquals(saved, clock.state(), state.name());
                assertEquals(0, changes.get(), state.name());
            }
            processor.inactive = null;
            clock.tick(processor, host);
            assertEquals((2 * count) % 8,
                clock.state().getOrDefault("worker", 0));
        }
    }

    @Test
    void extraWorkNeverBypassesTheProcessorsEnergyGate() {
        var clock = new ProcessorClock(() -> 8);
        var host = new FeatureHost(() -> {
        });
        var processor = new Consumer("worker");
        processor.energy = 5;
        clock.tick(processor, host);
        assertEquals(1, processor.ticks);
        assertEquals(0, processor.energy);
        assertFalse(processor.processing());
        processor.energy = 10;
        clock.tick(processor, host);
        assertEquals(3, processor.ticks);
    }
}
