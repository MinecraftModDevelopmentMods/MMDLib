package zone.moddev.mc.lib.machine.internals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static zone.moddev.mc.lib.machine.MachineAssertions.assertNear;
import static zone.moddev.mc.lib.machine.MachineAssertions.assertRejected;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.common.types.MaterialProperties;

class MachinePartsTest {
    /** The gear template from machine-ui.md, "Part files". */
    private static final String GEAR_TEMPLATE = """
        {
          "part": "mmdlib:gear",
          "socket": "gears",
          "modifiers": [
            { "stat": "speed",           "op": "add_multiplied_base", "amount": "0.08 * ($material.hardness - 3)" },
            { "stat": "energy_per_tick", "op": "add_multiplied_base", "amount": "0.03 * $material.hardness" }
          ],
          "durability": "500 * $material.hardness"
        }
        """;

    private static final Map<ResourceLocation, PartSubject> SUBJECTS = Map.of(
        id("test:iron_gear"), gear("test:iron_gear", 4, Map.of()),
        id("test:invar_gear"), gear("test:invar_gear", 6, Map.of("grip", 2.0)),
        id("test:soft_gear"), gear("test:soft_gear", 0, Map.of()),
        id("test:diamond_coated_gear"),
        gear("test:diamond_coated_gear", 10, Map.of()), id("test:plain_rod"),
        new PartSubject(id("test:plain_rod"), Optional.of(id("mmdlib:rod")),
            Optional.empty()),
        id("test:plain_pipe"), new PartSubject(id("test:plain_pipe"),
            Optional.empty(), Optional.empty()));

    @AfterEach
    void uninstall() {
        MachineParts.install(MachineParts.NONE);
    }

    @Test
    void templateAppliesWithTheMaterial() {
        var iron = parts().resolve(id("test:iron_gear")).orElseThrow();
        assertEquals("gears", iron.socket());
        assertNear(.08, iron.modifiers().get(0).amount());
        assertNear(.12, iron.modifiers().get(1).amount());
        assertEquals(AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
            iron.modifiers().get(0).op());
        assertEquals(OptionalInt.of(2000), iron.durability());
    }

    @Test
    void itemFileBeatsTheTemplate() {
        var coated = parts().resolve(id("test:diamond_coated_gear"))
            .orElseThrow();
        assertEquals(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
            coated.modifiers().get(0).op());
        assertEquals(.4, coated.modifiers().get(0).amount());
        assertTrue(coated.durability().isEmpty());
    }

    @Test
    void missingSpecialKeysCountAsZero() {
        var invar = parts().resolve(id("test:invar_gear")).orElseThrow();
        assertEquals(1, invar.modifiers().size());
        assertEquals(2, invar.modifiers().get(0).amount());
        assertTrue(invar.durability().isEmpty());
    }

    @Test
    void durabilityBelowOneIsNotAPart() {
        assertTrue(parts().resolve(id("test:soft_gear")).isEmpty());
    }

    @Test
    void itemsWithoutAMaterialCantUseMaterialVariables() {
        var parts = parts();
        assertTrue(parts.resolve(id("test:plain_pipe")).isEmpty());
        assertTrue(parts.resolve(id("test:plain_rod")).isEmpty());
        assertTrue(parts.resolve(id("test:unknown")).isEmpty());
    }

    @Test
    void resolvedPartsAreCached() {
        var parts = parts();
        assertSame(parts.resolve(id("test:iron_gear")).orElseThrow(),
            parts.resolve(id("test:iron_gear")).orElseThrow());
    }

    @Test
    void nonFiniteAmountIsNotAPart() {
        var rods = MachineParts.parse(
            Map.of(id("test:rod"),
                JsonParser.parseString(
                    """
                        {"part": "mmdlib:rod", "socket": "shafts",
                         "modifiers": [{"stat": "speed", "op": "add_value", "amount": "10 / $material.hardness"}]}
                        """)),
            item -> Optional
                .of(new PartSubject(item, Optional.of(id("mmdlib:rod")),
                    gear(item.toString(),
                        item.getPath().equals("zero_rod") ? 0 : 5, Map.of())
                        .material())));
        assertTrue(rods.resolve(id("test:zero_rod")).isEmpty());
        assertEquals(2, rods.resolve(id("test:steel_rod")).orElseThrow()
            .modifiers().get(0).amount());
    }

    @Test
    void refusesBadFiles() {
        assertRejected(
            () -> parsePart("{\"socket\":\"gears\",\"modifiers\":[]}"));
        assertRejected(() -> parsePart(
            "{\"part\":\"mmdlib:gear\",\"item\":\"test:x\",\"socket\":\"gears\",\"modifiers\":[]}"));
        assertRejected(() -> parsePart(
            GEAR_TEMPLATE.replace("$material.hardness - 3", "$speed")));
    }

    // A misspelt key is an error, not a part that quietly never wears.
    @Test
    void refusesUnknownKeys() {
        assertRejected(() -> parsePart(
            GEAR_TEMPLATE.replace("\"durability\"", "\"durabilty\"")));
        assertRejected(() -> parsePart(
            GEAR_TEMPLATE.replace("\"stat\"", "\"note\": 1, \"stat\"")));
    }

    @Test
    void checkReportsFilesThatDecodeButCantDoAnything() {
        var problems = filesToCheck(
            item -> Optional.ofNullable(SUBJECTS.get(item)))
            .check(SUBJECTS.keySet());
        assertEquals(3, problems.size(), problems.toString());
        assertMentioned(problems, "test:typo_part", "mmdlib:gaer");
        assertMentioned(problems, "test:typo_item", "test:iron_geer");
        assertMentioned(problems, "test:special", "$material.special.gripp");
    }

    // Template errors are logged when a datapack loads, not when an item is
    // first used.
    @Test
    void checkResolvesEveryItemUpFront() {
        var lookups = new int[1];
        var parts = filesToCheck(item -> {
            lookups[0]++;
            return Optional.ofNullable(SUBJECTS.get(item));
        });
        parts.check(SUBJECTS.keySet());
        int after = lookups[0];
        assertTrue(parts.resolve(id("test:iron_gear")).isPresent());
        assertTrue(parts.resolve(id("test:soft_gear")).isEmpty());
        assertEquals(after, lookups[0]);
    }

    @Test
    void installingBumpsTheGeneration() {
        var parts = parts();
        int generation = MachineParts.current().generation();
        MachineParts.install(parts);
        assertSame(parts, MachineParts.current());
        assertTrue(parts.generation() > generation);
    }

    /**
     * The gear template, an item file, a second template for the same part (the
     * lower resource ID, test:gear, wins), a file with an unknown variable
     * (skipped, the rest still load) and two more item files.
     */
    private static MachineParts parts() {
        var files = new HashMap<ResourceLocation, JsonElement>();
        files.put(id("test:gear"), JsonParser.parseString(GEAR_TEMPLATE));
        files.put(id("test:diamond_coated_gear"), JsonParser.parseString(
            """
                {
                  "item": "test:diamond_coated_gear",
                  "socket": "gears",
                  "modifiers": [
                    { "stat": "speed", "op": "add_multiplied_total", "amount": 0.4 },
                    { "stat": "energy_per_tick", "op": "add_value", "amount": 12 }
                  ]
                }
                """));
        files.put(id("test:zz_gear"), JsonParser
            .parseString(GEAR_TEMPLATE.replace("\"gears\"", "\"other\"")));
        files.put(id("test:broken"),
            JsonParser.parseString(
                GEAR_TEMPLATE.replace("mmdlib:gear", "mmdlib:plate")
                    .replace("$material.hardness - 3", "$material.colour")));
        files.put(id("test:pipe"), JsonParser.parseString(
            """
                {"item": "test:plain_pipe", "socket": "pipes",
                 "modifiers": [{"stat": "speed", "op": "add_value", "amount": "$material.special.grip + 1"}]}
                """));
        files.put(id("test:grip"), JsonParser.parseString(
            """
                {"item": "test:invar_gear", "socket": "gears",
                 "modifiers": [{"stat": "grip", "op": "add_value",
                                "amount": "$material.special.grip + $material.special.none"}]}
                """));
        return MachineParts.parse(files,
            item -> Optional.ofNullable(SUBJECTS.get(item)));
    }

    private static MachineParts filesToCheck(
        Function<ResourceLocation, Optional<PartSubject>> subjects) {
        var files = new HashMap<ResourceLocation, JsonElement>();
        files.put(id("test:gear"), JsonParser.parseString(GEAR_TEMPLATE));
        files.put(id("test:typo_part"), JsonParser
            .parseString(GEAR_TEMPLATE.replace("mmdlib:gear", "mmdlib:gaer")));
        files.put(id("test:typo_item"), JsonParser.parseString(
            "{\"item\": \"test:iron_geer\", \"socket\": \"gears\", \"modifiers\": []}"));
        files.put(id("test:special"), JsonParser.parseString(
            """
                {"item": "test:invar_gear", "socket": "gears",
                 "modifiers": [{"stat": "grip", "op": "add_value",
                                "amount": "$material.special.grip + $material.special.gripp"}]}
                """));
        return MachineParts.parse(files, subjects::apply);
    }

    private static void assertMentioned(List<String> problems,
        String... words) {
        assertTrue(
            problems.stream().anyMatch(
                problem -> List.of(words).stream().allMatch(problem::contains)),
            () -> "No problem mentions " + List.of(words) + " in " + problems);
    }

    private static PartSubject gear(String item, double hardness,
        Map<String, Double> special) {
        return new PartSubject(id(item), Optional.of(id("mmdlib:gear")),
            Optional.of(new MaterialProperties("metal", -1, hardness, 1, 0, 0,
                Set.of(), special)));
    }

    private static PartDefinition parsePart(String json) {
        return PartDefinition.CODEC
            .parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
