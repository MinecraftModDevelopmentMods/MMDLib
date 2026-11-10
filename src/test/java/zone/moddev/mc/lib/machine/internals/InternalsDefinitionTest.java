package zone.moddev.mc.lib.machine.internals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static zone.moddev.mc.lib.machine.MachineAssertions.assertRejected;

import java.util.List;

import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

class InternalsDefinitionTest {
    /** The internals example from machine-ui.md, "Sockets and stats". */
    private static final String INTERNALS = """
        {
          "open_with": "#c:tools/wrench",
          "swap": "when_idle",
          "sockets": {
            "gears": { "count": 2, "accepts": "#c:gears" },
            "motor": { "count": 1, "accepts": "#example:motors" },
            "coil":  { "count": 1, "accepts": "#example:heating_coils", "optional": true }
          },
          "stats": {
            "speed":           { "base": 1.0, "min": 0.25, "max": 8 },
            "energy_per_tick": { "base": 40, "min": 1 },
            "max_temperature": { "base": 900 }
          }
        }
        """;

    @Test
    void decodesTheDocsExample() {
        var internals = parse(INTERNALS);
        assertEquals(SwapRule.WHEN_IDLE, internals.swap());
        assertEquals(ResourceLocation.parse("c:tools/wrench"), internals
            .openWith().orElseThrow().value().left().orElseThrow().location());
        assertTrue(internals.sockets().get("coil").optional());
        assertFalse(internals.sockets().get("gears").optional());
        assertEquals(900, internals.stats().get("max_temperature").base());
    }

    @Test
    void laysOutSlotsSocketBySocketInNameOrder() {
        var internals = parse(INTERNALS);
        assertEquals(List.of("coil", "gears", "motor"),
            List.copyOf(internals.sockets().keySet()));
        assertEquals(4, internals.slotCount());
        assertEquals(List.of(0), internals.slots("coil"));
        assertEquals(List.of(1, 2), internals.slots("gears"));
        assertEquals(List.of(3), internals.slots("motor"));
    }

    @Test
    void fillsInDefaults() {
        var plain = parse(
            "{\"sockets\":{\"motor\":{\"accepts\":\"example:motor\"}},\"stats\":{}}");
        assertEquals(SwapRule.WHEN_IDLE, plain.swap());
        assertTrue(plain.openWith().isEmpty());
        assertEquals(1, plain.sockets().get("motor").count());
        assertEquals(ResourceLocation.parse("example:motor"),
            plain.sockets().get("motor").accepts().orElseThrow().value().right()
                .orElseThrow());
    }

    @Test
    void swapRules() {
        assertTrue(SwapRule.WHEN_IDLE.allows(false));
        assertFalse(SwapRule.WHEN_IDLE.allows(true));
        assertTrue(SwapRule.ALWAYS.allows(true));
        assertFalse(SwapRule.NEVER.allows(false));
    }

    @Test
    void refusesBadDefinitions() {
        assertRejected(
            () -> parse(INTERNALS.replace("\"count\": 2", "\"count\": 0")));
        assertRejected(
            () -> parse(INTERNALS.replace("when_idle", "sometimes")));
        assertRejected(() -> parse(INTERNALS.replace("#c:gears", "#")));
        assertRejected(() -> parse(INTERNALS.replace("#c:gears", "Not An Id")));
    }

    @Test
    void refusesAnUnknownSocket() {
        assertRejected(() -> parse(INTERNALS).slots("pump"));
    }

    @Test
    void findsTheSocketOfASlot() {
        var internals = parse(INTERNALS);
        assertEquals("coil", internals.socketAt(0));
        assertEquals("motor", internals.socketAt(3));
    }

    @Test
    void refusesASlotOutOfRange() {
        var internals = parse(INTERNALS);
        assertRejected(() -> internals.socketAt(-1));
        assertRejected(() -> internals.socketAt(4));
    }

    private static InternalsDefinition parse(String json) {
        return InternalsDefinition.CODEC
            .parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }
}
