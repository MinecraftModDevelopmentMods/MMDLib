package zone.moddev.mc.lib.machine.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static zone.moddev.mc.lib.machine.MachineAssertions.assertNear;
import static zone.moddev.mc.lib.machine.MachineAssertions.assertRejected;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import org.junit.jupiter.api.Test;

class MachineStatsTest {
  private static final Map<String, StatDefinition> DEFS =
      Map.of(
          "speed", new StatDefinition(1, OptionalDouble.of(.25), OptionalDouble.of(8)),
          "energy_per_tick", new StatDefinition(40, OptionalDouble.of(1), OptionalDouble.empty()));

  // The worked table in machine-ui.md, with example hardness iron 4 and invar 6.
  private static final List<StatModifier> IRON =
      List.of(base("speed", .08), base("energy_per_tick", .12));
  private static final List<StatModifier> INVAR =
      List.of(base("speed", .24), base("energy_per_tick", .18));
  private static final List<StatModifier> MOTOR =
      List.of(total("speed", .15), value("energy_per_tick", 8));
  private static final List<StatModifier> UPGRADE =
      List.of(total("speed", .5), total("energy_per_tick", .5));

  @Test
  void twoIronGears() {
    var stats = MachineStats.compute(DEFS, concat(IRON, IRON));
    assertNear(1.16, stats.get("speed"));
    assertNear(49.6, stats.get("energy_per_tick"));
  }

  @Test
  void twoInvarGears() {
    var stats = MachineStats.compute(DEFS, concat(INVAR, INVAR));
    assertNear(1.48, stats.get("speed"));
    assertNear(54.4, stats.get("energy_per_tick"));
  }

  @Test
  void invarGearsAndAMotor() {
    var stats = MachineStats.compute(DEFS, concat(INVAR, INVAR, MOTOR));
    assertNear(1.702, stats.get("speed"));
    assertNear(65.28, stats.get("energy_per_tick"));
  }

  @Test
  void invarGearsAMotorAndAnUpgrade() {
    var stats = MachineStats.compute(DEFS, concat(INVAR, INVAR, MOTOR, UPGRADE));
    assertNear(2.553, stats.get("speed"));
    assertNear(97.92, stats.get("energy_per_tick"));
  }

  @Test
  void orderDoesNotMatter() {
    var all = concat(INVAR, INVAR, MOTOR, UPGRADE);
    var reversed = new ArrayList<>(all);
    Collections.reverse(reversed);
    assertEquals(
        MachineStats.compute(DEFS, all).values(), MachineStats.compute(DEFS, reversed).values());
  }

  @Test
  void noModifiersGiveTheBase() {
    assertEquals(1, MachineStats.compute(DEFS, List.of()).get("speed"));
  }

  @Test
  void clampsToMinAndMax() {
    assertEquals(8, MachineStats.compute(DEFS, List.of(total("speed", 10))).get("speed"));
    assertEquals(.25, MachineStats.compute(DEFS, List.of(value("speed", -5))).get("speed"));
  }

  @Test
  void ignoresUndeclaredStats() {
    var stats = MachineStats.compute(DEFS, List.of(value("max_temperature", 400)));
    assertEquals(2, stats.values().size());
    assertEquals(1, stats.get("speed"));
  }

  @Test
  void refusesUnknownStatsAndNaN() {
    assertRejected(() -> MachineStats.EMPTY.get("speed"));
    assertRejected(() -> value("speed", Double.NaN));
  }

  @Test
  void decodesDefinitions() {
    assertEquals(DEFS.get("speed"), parse("{\"base\":1,\"min\":0.25,\"max\":8}"));
    assertTrue(parse("{\"base\":900}").min().isEmpty());
  }

  @Test
  void refusesBadDefinitions() {
    assertRejected(() -> parse("{\"base\":1,\"min\":5,\"max\":2}"));
    assertRejected(() -> parse("{\"min\":1}"));
  }

  private static StatDefinition parse(String json) {
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
  private static List<StatModifier> concat(List<StatModifier>... lists) {
    var all = new ArrayList<StatModifier>();
    for (var list : lists) all.addAll(list);
    return all;
  }
}
