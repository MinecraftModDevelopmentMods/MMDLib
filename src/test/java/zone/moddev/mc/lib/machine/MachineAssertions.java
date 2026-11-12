package zone.moddev.mc.lib.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;

import org.junit.jupiter.api.function.Executable;

/** Shared assertions and inputs for the machine module's unit tests. */
public final class MachineAssertions {
    private MachineAssertions() {
    }

    /** Asserts {@code a} and {@code b} agree to 1e-9. */
    public static void assertNear(double expected, double actual) {
        assertEquals(expected, actual, 1e-9);
    }

    /**
     * Asserts {@code action} refuses its input the way machine code does: with
     * an {@link IllegalArgumentException}, or the {@link IllegalStateException}
     * of a failed {@code getOrThrow}.
     */
    public static void assertRejected(Executable action) {
        try {
            action.execute();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return;
        } catch (Throwable other) {
            fail("Expected the input to be refused, got " + other, other);
        }
        fail("Expected the input to be refused, but it was accepted");
    }

    /** Expression variables with one value. */
    public static Function<String, OptionalDouble> vars(String name,
        double value) {
        var values = Map.of(name, value);
        return key -> values.containsKey(key)
            ? OptionalDouble.of(values.get(key))
            : OptionalDouble.empty();
    }

    /** Expression variables with no values. */
    public static Function<String, OptionalDouble> noVars() {
        return name -> OptionalDouble.empty();
    }
}
