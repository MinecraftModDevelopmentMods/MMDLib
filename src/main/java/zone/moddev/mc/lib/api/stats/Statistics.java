package zone.moddev.mc.lib.api.stats;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

import net.minecraft.network.chat.Component;

/**
 * Immutable descriptions of a part's statistics; sampling, formatting and sync belong to the UI.
 */
public record Statistics(Component title, List<Row> rows, List<EnergyUse> maximumUses) {
  public static final Statistics EMPTY = new Statistics(Component.empty(), List.of(), List.of());

  public Statistics {
    title = Objects.requireNonNull(title).copy();
    rows = List.copyOf(rows);
    maximumUses = List.copyOf(maximumUses);
    if (rows.size() > 64 || rows.stream().map(Row::id).distinct().count() != rows.size())
      throw new IllegalArgumentException("Statistics need at most 64 rows with unique IDs");
  }

  public Statistics(Component title, List<Row> rows) {
    this(title, rows, List.of());
  }

  /** Stable IDs keep each row's counter history independent of order and optional rows. */
  public record Row(
      String id,
      Component label,
      Value value,
      Component unit,
      int decimals,
      boolean perTimescale,
      Object demandResource) {
    public Row(
        String id,
        Component label,
        Value value,
        Component unit,
        int decimals,
        boolean perTimescale) {
      this(id, label, value, unit, decimals, perTimescale, null);
    }

    /** Show this row only while a provider declares positive demand for this resource. */
    public Row whenUsed(Object resource) {
      return new Row(
          id, label, value, unit, decimals, perTimescale, Objects.requireNonNull(resource));
    }

    public Row {
      Objects.requireNonNull(id);
      label = Objects.requireNonNull(label).copy();
      Objects.requireNonNull(value);
      unit = Objects.requireNonNull(unit).copy();
      if (decimals < 0 || decimals > 8) throw new IllegalArgumentException("Decimal precision");
    }
  }

  public sealed interface Value permits Counter, Rate, Text, MaximumUse {}

  /** A lifetime counter, measured over the most recent window (20..1200 game ticks). */
  public record Counter(BigInteger total, int windowTicks) implements Value {
    public Counter {
      if (total.signum() < 0 || windowTicks < 20 || windowTicks > 1200)
        throw new IllegalArgumentException("Invalid statistics counter");
    }
  }

  /** Exact amount per number of game ticks; a non-timescale row uses this as a fixed quantity. */
  public record Rate(BigInteger amount, long ticks) implements Value {
    public Rate {
      if (amount.signum() < 0 || ticks <= 0)
        throw new IllegalArgumentException("Invalid statistics rate");
    }
  }

  public record Text(Component text) implements Value {
    public Text {
      text = Objects.requireNonNull(text).copy();
    }
  }

  /** Resolves the combined demands of all registered providers against this exact resource. */
  public record MaximumUse(Object resource) implements Value {
    public MaximumUse {
      Objects.requireNonNull(resource);
    }
  }

  /** Maximum recipe demand per working tick. Resources are matched by identity, not equality. */
  public record EnergyUse(Object resource, BigInteger perTick) {
    public EnergyUse {
      Objects.requireNonNull(resource);
      if (perTick.signum() < 0) throw new IllegalArgumentException("Negative energy demand");
    }
  }

  public static Component label(String key) {
    return Component.translatable("screen.mmdlib.stats." + key);
  }

  public static Row rf(String id, Value value) {
    return new Row(id, label(id), value, Component.literal("RF"), 0, true);
  }

  public static Row transfer(String id, long maximum) {
    return rf(id, new Rate(BigInteger.valueOf(maximum), 1));
  }

  public static Row cycles(String id, Value value) {
    return new Row(id, label(id), value, label("cycles_unit"), 2, true);
  }
}
