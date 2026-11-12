package zone.moddev.mc.lib.machine.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class VisualVariablesTest {
    @Test
    void customComponentsComposeWithoutRendererKnowledgeAndSnapshotsAreDetached() {
        var temperature = new AtomicInteger(12);
        VisualVariableProvider kiln = vars -> vars.add("temperature", 0,
            temperature::get);
        var variables = new VisualVariables.Builder()
            .component("custom.kiln", kiln).add("unused", false, () -> {
                throw new AssertionError("Unreferenced getter sampled");
            }).build();
        var first = variables.snapshot(Set.of("custom.kiln.temperature"));
        temperature.set(80);
        assertEquals(Map.of("custom.kiln.temperature", 12.0), first);
        assertEquals(Map.of("custom.kiln.temperature", 80.0),
            variables.snapshot(first.keySet()));
        assertThrows(UnsupportedOperationException.class,
            () -> first.put("custom.kiln.temperature", 2.0));
        assertEquals(0.0, variables.defaults().get("custom.kiln.temperature"));
    }

    @Test
    void rejectsCollisionsTypeChangesAndNonFiniteValues() {
        var builder = new VisualVariables.Builder().component("a",
            vars -> vars.add("running", false, () -> true));
        assertThrows(IllegalArgumentException.class,
            () -> builder.add("a.running", false, () -> false));
        var invalid = new VisualVariables.Builder()
            .add("value", 0.0, () -> "wrong").build();
        assertThrows(IllegalArgumentException.class,
            () -> invalid.snapshot(Set.of("value")));
        assertThrows(IllegalArgumentException.class,
            () -> invalid.snapshot(Set.of("missing")));
        assertThrows(IllegalArgumentException.class,
            () -> new VisualVariables.Builder().add("value", Double.NaN,
                () -> 0));
        var infinity = new VisualVariables.Builder()
            .add("value", 0.0, () -> Double.POSITIVE_INFINITY).build();
        assertThrows(IllegalArgumentException.class,
            () -> infinity.snapshot(Set.of("value")));
    }
}
