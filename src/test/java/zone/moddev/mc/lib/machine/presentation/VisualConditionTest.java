package zone.moddev.mc.lib.machine.presentation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class VisualConditionTest {
    private static final Map<String, Object> DEFAULTS = Map.of(
        "processors.mixer.running", false, "processors.mixer.status", "idle",
        "tanks.water.fill", 0.0, "inventories.output.count", 0.0,
        "tanks.input/0.fill", 0.0);

    @Test
    void combinesIndependentComponentsWithTypedComparisonsAndArithmetic() {
        var condition = VisualCondition.compile(
            "($processors.mixer.running && $tanks.water.fill >= 0.5) || "
                + "($processors.mixer.status == 'blocked' && $inventories.output.count / 2 > 3)",
            DEFAULTS);
        assertFalse(condition.test(DEFAULTS));
        assertTrue(condition.test(Map.of("processors.mixer.running", true,
            "tanks.water.fill", 0.75)));
        assertFalse(condition.test(Map.of("processors.mixer.running", true,
            "tanks.water.fill", 0.25)));
        assertTrue(condition.test(Map.of("processors.mixer.status", "blocked",
            "inventories.output.count", 8.0)));
        assertFalse(condition.test(Map.of("processors.mixer.status", "blocked",
            "inventories.output.count", 6.0)));
    }

    @Test
    void supportsDefaultsNegationPrecedenceAndEscapedComponentIds() {
        assertTrue(VisualCondition
            .compile("!$processors.mixer.running", DEFAULTS).test(Map.of()));
        assertTrue(VisualCondition.compile("true || false && false", DEFAULTS)
            .test(Map.of()));
        assertTrue(VisualCondition
            .compile("${tanks.input/0.fill} == 0", DEFAULTS).test(Map.of()));
        assertTrue(VisualCondition.compile("-2 + 3 * 4 == 10", DEFAULTS)
            .test(Map.of()));
    }

    @Test
    void rejectsUnknownVariablesInvalidTypesAndMalformedConditionsAtLoadTime() {
        for (var source : new String[]{"$processors.typo.running",
            "$tanks.water.fill", "$tanks.water.fill && true",
            "$processors.mixer.running > 0", "$processors.mixer.status == 1",
            "true junk", "true ||", "(true", "'unterminated",
            "(".repeat(70) + "true" + ")".repeat(70)})
            assertThrows(IllegalArgumentException.class,
                () -> VisualCondition.compile(source, DEFAULTS), source);
    }
}
