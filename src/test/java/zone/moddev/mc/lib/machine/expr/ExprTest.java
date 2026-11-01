package zone.moddev.mc.lib.machine.expr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static zone.moddev.mc.lib.machine.MachineAssertions.assertNear;
import static zone.moddev.mc.lib.machine.MachineAssertions.assertRejected;
import static zone.moddev.mc.lib.machine.MachineAssertions.noVars;
import static zone.moddev.mc.lib.machine.MachineAssertions.vars;

import java.util.Set;

import com.google.gson.JsonPrimitive;

import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

class ExprTest {
  @Test
  void evaluatesMaterialVariables() {
    assertNear(
        .24, Expr.parse("0.08 * ($material.hardness - 3)").eval(vars("material.hardness", 6)));
  }

  @Test
  void followsPrecedenceAndAssociativity() {
    assertEquals(10, Expr.parse("-2 + 3 * 4").eval(noVars()));
    assertEquals(.75, Expr.parse("(1 + 2) / 4").eval(noVars()));
    assertEquals(3, Expr.parse("10 - 4 - 3").eval(noVars()));
    assertEquals(-4, Expr.parse("2 * -(1 + 1)").eval(noVars()));
    assertEquals(3, Expr.constant(3).eval(noVars()));
  }

  @Test
  void listsItsVariables() {
    assertEquals(Set.of("a.b"), Expr.parse("$a.b").variables());
    assertTrue(Expr.parse("1 + 2").variables().isEmpty());
  }

  @Test
  void divisionByZeroIsInfiniteNotAnError() {
    assertTrue(Double.isInfinite(Expr.parse("1 / $x").eval(vars("x", 0))));
  }

  @Test
  void refusesMalformedText() {
    assertRejected(() -> Expr.parse("2 *"));
    assertRejected(() -> Expr.parse("2 $"));
    assertRejected(() -> Expr.parse("(1"));
    assertRejected(() -> Expr.parse(""));
  }

  @Test
  void refusesMalformedVariableNames() {
    assertRejected(() -> Expr.parse("$a..b"));
    assertRejected(() -> Expr.parse("$a."));
    assertRejected(() -> Expr.parse("$.a"));
  }

  @Test
  void refusesAMissingVariable() {
    assertRejected(() -> Expr.parse("$missing").eval(noVars()));
  }

  @Test
  void decodesNumbersAndStrings() {
    assertEquals(
        12, Expr.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(12)).getOrThrow().eval(noVars()));
    assertEquals(
        8,
        Expr.CODEC
            .parse(JsonOps.INSTANCE, new JsonPrimitive("$x * 2"))
            .getOrThrow()
            .eval(vars("x", 4)));
    assertTrue(Expr.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("2 *")).error().isPresent());
  }

  // Double.toString would write these in E notation, which the parser doesn't read.
  @Test
  void roundTripsLargeAndSmallNumbers() {
    var encoded =
        Expr.CODEC.encodeStart(JsonOps.INSTANCE, Expr.parse("10000000 * $x + 0.0001")).getOrThrow();
    assertNear(
        20000000.0001, Expr.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().eval(vars("x", 2)));
  }
}
