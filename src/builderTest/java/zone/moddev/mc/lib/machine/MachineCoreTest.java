package zone.moddev.mc.lib.machine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import zone.moddev.mc.lib.common.types.MaterialProperties;
import zone.moddev.mc.lib.machine.expr.Expr;
import zone.moddev.mc.lib.machine.ingredient.AmountMatch;
import zone.moddev.mc.lib.machine.internals.InternalsDefinition;
import zone.moddev.mc.lib.machine.internals.MachineParts;
import zone.moddev.mc.lib.machine.internals.PartDefinition;
import zone.moddev.mc.lib.machine.internals.PartSubject;
import zone.moddev.mc.lib.machine.internals.SwapRule;
import zone.moddev.mc.lib.machine.recipe.InputClaims;
import zone.moddev.mc.lib.machine.stat.MachineStats;
import zone.moddev.mc.lib.machine.stat.StatDefinition;
import zone.moddev.mc.lib.machine.stat.StatModifier;

/** Checks the machine module's core types, which need no running game. */
public final class MachineCoreTest {
  public static void main(String[] args) {
    expressions();
    stats();
    internalsDefinition();
    parts();
    amountMatch();
    inputClaims();
    System.out.println(
        "Machine expressions, stats, internals definitions, part files, amount matching and input"
            + " claims passed");
  }

  private static void expressions() {
    require(
        near(Expr.parse("0.08 * ($material.hardness - 3)").eval(v("material.hardness", 6)), .24));
    require(Expr.parse("-2 + 3 * 4").eval(none()) == 10);
    require(Expr.parse("(1 + 2) / 4").eval(none()) == .75);
    require(Expr.parse("10 - 4 - 3").eval(none()) == 3);
    require(Expr.parse("2 * -(1 + 1)").eval(none()) == -4);
    require(Expr.parse("$a.b").variables().equals(Set.of("a.b")));
    require(Expr.parse("1 + 2").variables().isEmpty());
    require(Double.isInfinite(Expr.parse("1 / $x").eval(v("x", 0))));
    require(Expr.constant(3).eval(none()) == 3);
    expectFailure(() -> Expr.parse("2 *"));
    expectFailure(() -> Expr.parse("2 $"));
    expectFailure(() -> Expr.parse("(1"));
    expectFailure(() -> Expr.parse(""));
    expectFailure(() -> Expr.parse("$missing").eval(none()));
    require(
        Expr.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(12)).getOrThrow().eval(none()) == 12);
    require(
        Expr.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("$x * 2")).getOrThrow().eval(v("x", 4))
            == 8);
    require(Expr.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("2 *")).error().isPresent());
    // Encoding round-trips, including numbers Double.toString would write in E notation.
    var big = Expr.parse("10000000 * $x + 0.0001");
    var encoded = Expr.CODEC.encodeStart(JsonOps.INSTANCE, big).getOrThrow();
    require(
        near(
            Expr.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().eval(v("x", 2)),
            20000000.0001));
  }

  private static void stats() {
    var defs =
        Map.of(
            "speed", new StatDefinition(1, OptionalDouble.of(.25), OptionalDouble.of(8)),
            "energy_per_tick",
                new StatDefinition(40, OptionalDouble.of(1), OptionalDouble.empty()));
    // The worked table in machine-ui.md, with example hardness iron 4 and invar 6.
    var iron = List.of(base("speed", .08), base("energy_per_tick", .12));
    var invar = List.of(base("speed", .24), base("energy_per_tick", .18));
    var motor = List.of(total("speed", .15), value("energy_per_tick", 8));
    var upgrade = List.of(total("speed", .5), total("energy_per_tick", .5));
    var twoIron = MachineStats.compute(defs, concat(iron, iron));
    require(near(twoIron.get("speed"), 1.16) && near(twoIron.get("energy_per_tick"), 49.6));
    var twoInvar = MachineStats.compute(defs, concat(invar, invar));
    require(near(twoInvar.get("speed"), 1.48) && near(twoInvar.get("energy_per_tick"), 54.4));
    var withMotor = MachineStats.compute(defs, concat(invar, invar, motor));
    require(near(withMotor.get("speed"), 1.702) && near(withMotor.get("energy_per_tick"), 65.28));
    var all = concat(invar, invar, motor, upgrade);
    var full = MachineStats.compute(defs, all);
    require(near(full.get("speed"), 2.553) && near(full.get("energy_per_tick"), 97.92));
    var shuffled = new ArrayList<>(all);
    Collections.reverse(shuffled);
    require(MachineStats.compute(defs, shuffled).values().equals(full.values()));
    require(MachineStats.compute(defs, List.of()).get("speed") == 1);
    require(MachineStats.compute(defs, List.of(total("speed", 10))).get("speed") == 8);
    require(MachineStats.compute(defs, List.of(value("speed", -5))).get("speed") == .25);
    var undeclared = MachineStats.compute(defs, List.of(value("max_temperature", 400)));
    require(undeclared.values().size() == 2 && undeclared.get("speed") == 1);
    expectFailure(() -> MachineStats.EMPTY.get("speed"));
    expectFailure(() -> value("speed", Double.NaN));
    var parsed = parseStat("{\"base\":1,\"min\":0.25,\"max\":8}");
    require(parsed.equals(defs.get("speed")));
    require(parseStat("{\"base\":900}").min().isEmpty());
    expectFailure(() -> parseStat("{\"base\":1,\"min\":5,\"max\":2}"));
    expectFailure(() -> parseStat("{\"min\":1}"));
  }

  /** The internals example from machine-ui.md, "Sockets and stats". */
  static final String INTERNALS =
      """
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

  private static void internalsDefinition() {
    var internals = parseInternals(INTERNALS);
    require(internals.swap() == SwapRule.WHEN_IDLE);
    require(List.copyOf(internals.sockets().keySet()).equals(List.of("coil", "gears", "motor")));
    require(internals.slotCount() == 4);
    require(internals.slots("coil").equals(List.of(0)));
    require(internals.slots("gears").equals(List.of(1, 2)));
    require(internals.slots("motor").equals(List.of(3)));
    require(
        internals.sockets().get("coil").optional() && !internals.sockets().get("gears").optional());
    require(
        internals
            .openWith()
            .orElseThrow()
            .value()
            .left()
            .orElseThrow()
            .location()
            .equals(ResourceLocation.parse("c:tools/wrench")));
    require(internals.stats().get("max_temperature").base() == 900);
    var plain =
        parseInternals("{\"sockets\":{\"motor\":{\"accepts\":\"example:motor\"}},\"stats\":{}}");
    require(plain.swap() == SwapRule.WHEN_IDLE && plain.openWith().isEmpty());
    require(plain.sockets().get("motor").count() == 1);
    require(
        plain
            .sockets()
            .get("motor")
            .accepts()
            .orElseThrow()
            .value()
            .right()
            .orElseThrow()
            .equals(ResourceLocation.parse("example:motor")));
    require(SwapRule.WHEN_IDLE.allows(false) && !SwapRule.WHEN_IDLE.allows(true));
    require(SwapRule.ALWAYS.allows(true) && !SwapRule.NEVER.allows(false));
    expectFailure(() -> parseInternals(INTERNALS.replace("\"count\": 2", "\"count\": 0")));
    expectFailure(() -> parseInternals(INTERNALS.replace("when_idle", "sometimes")));
    expectFailure(() -> parseInternals(INTERNALS.replace("#c:gears", "#")));
    expectFailure(() -> parseInternals(INTERNALS.replace("#c:gears", "Not An Id")));
    expectFailure(() -> internals.slots("pump"));
  }

  /** The gear template from machine-ui.md, "Part files". */
  static final String GEAR_TEMPLATE =
      """
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

  private static void parts() {
    var subjects =
        Map.of(
            id("test:iron_gear"), gear("test:iron_gear", 4, Map.of()),
            id("test:invar_gear"), gear("test:invar_gear", 6, Map.of("grip", 2.0)),
            id("test:soft_gear"), gear("test:soft_gear", 0, Map.of()),
            id("test:diamond_coated_gear"), gear("test:diamond_coated_gear", 10, Map.of()),
            id("test:plain_rod"),
                new PartSubject(
                    id("test:plain_rod"), Optional.of(id("mmdlib:rod")), Optional.empty()),
            id("test:plain_pipe"),
                new PartSubject(id("test:plain_pipe"), Optional.empty(), Optional.empty()));
    var files = new HashMap<ResourceLocation, JsonElement>();
    files.put(id("test:gear"), JsonParser.parseString(GEAR_TEMPLATE));
    files.put(
        id("test:diamond_coated_gear"),
        JsonParser.parseString(
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
    // A second template for the same part: the lower resource ID (test:gear) wins.
    files.put(
        id("test:zz_gear"),
        JsonParser.parseString(GEAR_TEMPLATE.replace("\"gears\"", "\"other\"")));
    // An unknown variable: logged and skipped, the rest still load.
    files.put(
        id("test:broken"),
        JsonParser.parseString(
            GEAR_TEMPLATE
                .replace("mmdlib:gear", "mmdlib:plate")
                .replace("$material.hardness - 3", "$material.colour")));
    files.put(
        id("test:pipe"),
        JsonParser.parseString(
            """
{"item": "test:plain_pipe", "socket": "pipes",
 "modifiers": [{"stat": "speed", "op": "add_value", "amount": "$material.special.grip + 1"}]}
"""));
    files.put(
        id("test:grip"),
        JsonParser.parseString(
            """
            {"item": "test:invar_gear", "socket": "gears",
             "modifiers": [{"stat": "grip", "op": "add_value",
                            "amount": "$material.special.grip + $material.special.none"}]}
            """));
    var parts = MachineParts.parse(files, item -> Optional.ofNullable(subjects.get(item)));
    var iron = parts.resolve(id("test:iron_gear")).orElseThrow();
    require(iron.socket().equals("gears"));
    require(near(iron.modifiers().get(0).amount(), .08));
    require(near(iron.modifiers().get(1).amount(), .12));
    require(iron.modifiers().get(0).op() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    require(iron.durability().equals(OptionalInt.of(2000)));
    // An item file beats the template; missing special keys count as 0.
    var invar = parts.resolve(id("test:invar_gear")).orElseThrow();
    require(invar.modifiers().size() == 1 && invar.modifiers().get(0).amount() == 2);
    require(invar.durability().isEmpty());
    var coated = parts.resolve(id("test:diamond_coated_gear")).orElseThrow();
    require(coated.modifiers().get(0).op() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    require(coated.modifiers().get(0).amount() == .4 && coated.durability().isEmpty());
    // Hardness 0: the template's durability is 0, so this gear is not a part.
    require(parts.resolve(id("test:soft_gear")).isEmpty());
    // An item without a material can't use $material.
    require(parts.resolve(id("test:plain_pipe")).isEmpty());
    require(parts.resolve(id("test:plain_rod")).isEmpty());
    require(parts.resolve(id("test:unknown")).isEmpty());
    require(parts.resolve(id("test:iron_gear")).orElseThrow() == iron); // cached
    // A non-finite amount (10 / hardness 0) makes the item not a part.
    var rods =
        MachineParts.parse(
            Map.of(
                id("test:rod"),
                JsonParser.parseString(
                    """
{"part": "mmdlib:rod", "socket": "shafts",
 "modifiers": [{"stat": "speed", "op": "add_value", "amount": "10 / $material.hardness"}]}
""")),
            item ->
                Optional.of(
                    new PartSubject(
                        item,
                        Optional.of(id("mmdlib:rod")),
                        gear(item.toString(), item.getPath().equals("zero_rod") ? 0 : 5, Map.of())
                            .material())));
    require(rods.resolve(id("test:zero_rod")).isEmpty());
    require(rods.resolve(id("test:steel_rod")).orElseThrow().modifiers().get(0).amount() == 2);
    expectFailure(() -> parsePart("{\"socket\":\"gears\",\"modifiers\":[]}"));
    expectFailure(
        () ->
            parsePart(
                "{\"part\":\"mmdlib:gear\",\"item\":\"test:x\",\"socket\":\"gears\",\"modifiers\":[]}"));
    expectFailure(() -> parsePart(GEAR_TEMPLATE.replace("$material.hardness - 3", "$speed")));
    int generation = MachineParts.current().generation();
    MachineParts.install(parts);
    require(MachineParts.current() == parts && parts.generation() > generation);
    MachineParts.install(MachineParts.NONE);
  }

  private static void amountMatch() {
    require(AmountMatch.EXACT.test(5, 5) && !AmountMatch.EXACT.test(5, 6));
    require(AmountMatch.AT_LEAST.test(5, 6) && AmountMatch.AT_LEAST.test(5, 5));
    require(!AmountMatch.AT_LEAST.test(5, 4));
    require(AmountMatch.ANY_AMOUNT.test(5, 0));
    require(AmountMatch.AT_LEAST.test(3_000_000_000L, 3_000_000_001L));
    require(!AmountMatch.AT_LEAST.test(3_000_000_000L, 2_999_999_999L));
  }

  private static void inputClaims() {
    // A broad part listed first moves off the only entry a later part accepts.
    var two = new InputClaims(new long[] {1, 1});
    require(two.claim(1, any -> true) == 0);
    require(two.claim(1, entry -> entry == 0) == 0);
    require(two.claimed(0) == 1 && two.claimed(1) == 1);
    // Listing them the other way round gives the same answer.
    var swapped = new InputClaims(new long[] {1, 1});
    require(swapped.claim(1, entry -> entry == 0) == 0);
    require(swapped.claim(1, any -> true) == 0);
    require(swapped.claimed(0) == 1 && swapped.claimed(1) == 1);
    // A chain: the third part moves the second, which moves the first.
    var chain = new InputClaims(new long[] {1, 1, 1});
    require(chain.claim(1, entry -> entry != 1) == 0);
    require(chain.claim(1, entry -> entry != 2) == 0);
    require(chain.claim(1, entry -> entry == 0) == 0);
    require(chain.claimed(0) == 1 && chain.claimed(1) == 1 && chain.claimed(2) == 1);
    // Part of a stack moves, and the rest stays.
    var stacks = new InputClaims(new long[] {3, 3});
    require(stacks.claim(4, any -> true) == 0);
    require(stacks.claim(2, entry -> entry == 0) == 0);
    require(stacks.claimed(0) == 3 && stacks.claimed(1) == 3);
    // Nothing counts twice, and a claim that can't be met reports how much it's short.
    var shared = new InputClaims(new long[] {3});
    require(shared.claim(2, any -> true) == 0);
    require(shared.claim(2, any -> true) == 1);
    require(shared.claimed(0) == 3);
    // Two parts that only accept one entry can't both be met by moving.
    var stuck = new InputClaims(new long[] {1, 1});
    require(stuck.claim(1, entry -> entry == 0) == 0);
    require(stuck.claim(1, entry -> entry == 0) == 1);
    require(stuck.claimed(0) == 1 && stuck.claimed(1) == 0);
    // Empty entries aren't asked about, so an ingredient never tests an empty stack.
    var empty = new InputClaims(new long[] {0, 2});
    require(
        empty.claim(
                1,
                entry -> {
                  require(entry == 1);
                  return true;
                })
            == 0);
    // Energy amounts don't fit an int.
    var energy = new InputClaims(new long[] {3_000_000_000L});
    require(energy.claim(2_999_999_999L, any -> true) == 0);
    require(energy.claim(2, any -> true) == 1);
  }

  private static PartSubject gear(String item, double hardness, Map<String, Double> special) {
    return new PartSubject(
        id(item),
        Optional.of(id("mmdlib:gear")),
        Optional.of(new MaterialProperties("metal", -1, hardness, 1, 0, 0, Set.of(), special)));
  }

  private static PartDefinition parsePart(String json) {
    return PartDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
  }

  private static ResourceLocation id(String value) {
    return ResourceLocation.parse(value);
  }

  static InternalsDefinition parseInternals(String json) {
    return InternalsDefinition.CODEC
        .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
        .getOrThrow();
  }

  private static StatDefinition parseStat(String json) {
    return StatDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
  }

  private static StatModifier value(String stat, double amount) {
    return new StatModifier(stat, AttributeModifier.Operation.ADD_VALUE, amount);
  }

  private static StatModifier base(String stat, double amount) {
    return new StatModifier(stat, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, amount);
  }

  private static StatModifier total(String stat, double amount) {
    return new StatModifier(stat, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amount);
  }

  @SafeVarargs
  private static <T> List<T> concat(List<T>... lists) {
    var all = new ArrayList<T>();
    for (var list : lists) all.addAll(list);
    return all;
  }

  private static Function<String, OptionalDouble> v(String name, double value) {
    var values = Map.of(name, value);
    return key ->
        values.containsKey(key) ? OptionalDouble.of(values.get(key)) : OptionalDouble.empty();
  }

  private static Function<String, OptionalDouble> none() {
    return name -> OptionalDouble.empty();
  }

  static boolean near(double a, double b) {
    return Math.abs(a - b) < 1e-9;
  }

  static void require(boolean condition) {
    if (!condition) throw new AssertionError("Machine core assertion failed");
  }

  static void expectFailure(Runnable action) {
    try {
      action.run();
    } catch (IllegalArgumentException | IllegalStateException ex) {
      return;
    }
    throw new AssertionError("Invalid machine input accepted");
  }
}
