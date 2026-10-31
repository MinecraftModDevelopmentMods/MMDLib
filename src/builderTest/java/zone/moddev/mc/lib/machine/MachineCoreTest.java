package zone.moddev.mc.lib.machine;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;

import com.google.gson.JsonPrimitive;

import com.mojang.serialization.JsonOps;

import zone.moddev.mc.lib.machine.expr.Expr;

/** Checks the machine module's core types, which need no running game. */
public final class MachineCoreTest {
  public static void main(String[] args) {
    expressions();
    System.out.println("Machine expressions passed");
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
