package zone.moddev.mc.lib.machine.stat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A machine's stat values after every modifier, worked out by {@link #compute}. Immutable.
 *
 * <p>For each stat, starting from its base: add every {@code add_value} amount; add that result
 * times the sum of every {@code add_multiplied_base} amount; multiply by (1 + amount) for each
 * {@code add_multiplied_total} amount; clamp. Amounts are combined in sorted order, so the result
 * never depends on the order modifiers arrive in.
 */
public final class MachineStats {
  /** No stats at all. */
  public static final MachineStats EMPTY = new MachineStats(Map.of());

  private final Map<String, Double> values;

  private MachineStats(Map<String, Double> values) {
    this.values = Collections.unmodifiableMap(values);
  }

  /** Applies {@code modifiers} to {@code stats}; modifiers for undeclared stats are ignored. */
  public static MachineStats compute(
      Map<String, StatDefinition> stats, Collection<StatModifier> modifiers) {
    var values = new TreeMap<String, Double>();
    stats.forEach(
        (name, definition) -> {
          var add = new ArrayList<Double>();
          var base = new ArrayList<Double>();
          var total = new ArrayList<Double>();
          for (var modifier : modifiers) {
            if (!modifier.stat().equals(name)) continue;
            switch (modifier.op()) {
              case ADD_VALUE -> add.add(modifier.amount());
              case ADD_MULTIPLIED_BASE -> base.add(modifier.amount());
              case ADD_MULTIPLIED_TOTAL -> total.add(modifier.amount());
            }
          }
          double value = definition.base() + sum(add);
          value += value * sum(base);
          Collections.sort(total);
          for (double amount : total) value *= 1 + amount;
          values.put(name, definition.clamp(value));
        });
    return new MachineStats(values);
  }

  /**
   * The value of {@code stat}.
   *
   * @throws IllegalArgumentException if the machine doesn't declare {@code stat}
   */
  public double get(String stat) {
    var value = values.get(stat);
    if (value == null) throw new IllegalArgumentException("Machine has no stat " + stat);
    return value;
  }

  /** Every stat's value, by name. */
  public Map<String, Double> values() {
    return values;
  }

  private static double sum(List<Double> amounts) {
    Collections.sort(amounts);
    double sum = 0;
    for (double amount : amounts) sum += amount;
    return sum;
  }

  @Override
  public String toString() {
    return "MachineStats" + values;
  }
}
