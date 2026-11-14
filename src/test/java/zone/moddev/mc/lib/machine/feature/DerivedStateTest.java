package zone.moddev.mc.lib.machine.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import com.mojang.serialization.Codec;

import org.junit.jupiter.api.Test;

class DerivedStateTest {
    @Test
    void dependenciesRefreshInOrderAndPublishOneConsistentBatch() {
        var state = new ProcessorState();
        var input = state.define("input", Codec.INT, 1);
        var unrelated = state.define("unrelated", Codec.INT, 0);
        var evaluations = new AtomicInteger();
        var doubled = state.derive("doubled", Codec.INT,
            UnaryOperator.identity(), true, true, List.of(input), s -> {
                evaluations.incrementAndGet();
                return s.get(input) * 2;
            });
        var total = state.derive("total", Codec.INT, UnaryOperator.identity(),
            true, true, List.of(doubled), s -> s.get(doubled) + 1);
        var notifications = new AtomicInteger();
        state.onChange(() -> {
            assertEquals(9, state.get(total));
            notifications.incrementAndGet();
        });
        state.update(() -> {
            state.set(input, 3);
            state.set(input, 4);
            assertEquals(9, state.get(total));
        });
        assertEquals(1, notifications.get());
        assertEquals(2, evaluations.get());
        state.update(() -> state.set(unrelated, 1));
        assertEquals(2, evaluations.get());
        assertThrows(IllegalStateException.class,
            () -> state.update(() -> state.set(total, 42)));
        assertThrows(IllegalArgumentException.class,
            () -> state.portable(total));
    }

    @Test
    void serverRecomputesButClientsAcceptAuthoritativeDerivedSnapshots() {
        var state = new ProcessorState();
        var input = state.define("input", Codec.INT, 1,
            UnaryOperator.identity(), false, false);
        var derived = state.derive("display", Codec.INT,
            UnaryOperator.identity(), false, true, List.of(input),
            s -> s.get(input) * 2);
        var saved = state.save(false);
        assertFalse(saved.contains("display"));
        saved.putInt("input", 5);
        saved.putInt("display", 123);
        state.load(saved, false);
        assertEquals(10, state.get(derived));
        var client = state.save(true);
        client.putInt("display", 17);
        state.load(client, true);
        assertEquals(17, state.get(derived));
        assertThrows(IllegalArgumentException.class,
            () -> state.derive("bad", Codec.INT, UnaryOperator.identity(),
                false, false, List.of(), s -> s.get(input)));
    }
}
