package zone.moddev.mc.lib.machine.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProcessorTimerTest {
    @Test
    void countdownPoliciesHandleIdleActivePausedAndHeldTicks() {
        var processor = new Fixture();
        processor.initialize("timer", null);
        var host = new FeatureHost(() -> {
        }).add(processor);
        processor.idleTimer.start(3);
        processor.alwaysTimer.start(4);
        processor.tick(host); // idle before process starts
        assertEquals(2, processor.idleTimer.remaining());
        processor.tick(host); // active operation
        assertEquals(2, processor.idleTimer.remaining());
        assertEquals(2, processor.alwaysTimer.remaining());
        processor.setPaused(true);
        processor.tick(host);
        assertEquals(2, processor.idleTimer.remaining());
        assertEquals(1, processor.alwaysTimer.remaining());
        processor.suspended(host);
        assertTrue(processor.alwaysTimer.ready());
        assertEquals(2, processor.idleTimer.remaining());
        assertFalse(processor.clientState().contains("idleTimer"));
        assertEquals(2, processor.state().getInt("idleTimer"));
    }

    @Test
    void largeDeltasClampAndRestorePreservesRemainingTime() {
        var state = new ProcessorState();
        var timer = state.timer("timer", 40, ProcessorTimer.Policy.IDLE);
        timer.start(40);
        var saved = state.save(false);
        state.update(() -> state.advanceTimers(Integer.MAX_VALUE,
            Processor.ProcessState.IDLE));
        assertTrue(timer.ready());
        state.load(saved, false);
        assertEquals(40, timer.remaining());
        assertThrows(IllegalStateException.class, () -> timer.start(41));
        assertThrows(IllegalArgumentException.class, () -> state.update(
            () -> state.advanceTimers(-1, Processor.ProcessState.IDLE)));
    }

    private static final class Fixture extends StoredProcessor {
        final ProcessorTimer idleTimer = variables.timer("idleTimer", 10,
            ProcessorTimer.Policy.IDLE);
        final ProcessorTimer alwaysTimer = variables.timer("alwaysTimer", 10,
            ProcessorTimer.Policy.ALWAYS);
        public boolean canStart(ProcessorState state,
            ProcessorMachine context) {
            return true;
        }
        public ProcessState process(ProcessorState state,
            ProcessorMachine context, int deltaTicks) {
            return ProcessState.RUNNING;
        }
    }
}
