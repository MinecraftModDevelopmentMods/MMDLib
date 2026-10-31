package zone.moddev.mc.lib.machine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import zone.moddev.mc.lib.machine.expr.Expr;
import zone.moddev.mc.lib.machine.internals.InternalsDefinition;
import zone.moddev.mc.lib.machine.internals.SwapRule;
import zone.moddev.mc.lib.machine.stat.MachineStats;
import zone.moddev.mc.lib.machine.stat.StatDefinition;
import zone.moddev.mc.lib.machine.stat.StatModifier;

/** Checks the machine module's core types, which need no running game. */
public final class MachineCoreTest {
  public static void main(String[] args) {
    expressions();
    stats();
    internalsDefinition();
    System.out.println("Machine expressions, stats and internals definitions passed");
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
