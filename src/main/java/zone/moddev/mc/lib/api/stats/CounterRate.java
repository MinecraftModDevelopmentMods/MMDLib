package zone.moddev.mc.lib.api.stats;

import java.math.BigInteger;
import java.util.ArrayDeque;

/** Rolling sample of a provider counter, using game time. */
public final class CounterRate {
  private record Sample(long tick, BigInteger count) {}

  private final int window;

  public CounterRate(int window) {
    this.window = window;
  }

  private final ArrayDeque<Sample> samples = new ArrayDeque<>();

  public void sample(BigInteger count, long tick) {
    if (!samples.isEmpty()
        && (tick <= samples.getLast().tick() || count.compareTo(samples.getLast().count()) < 0))
      samples.clear();
    samples.addLast(new Sample(tick, count));
    while (samples.size() > 1 && samples.getFirst().tick() < tick - window) samples.removeFirst();
  }

  public String format(int ticksPerUnit, int decimals) {
    if (samples.size() < 2) return "—";
    var first = samples.getFirst();
    var last = samples.getLast();
    return StatsNumbers.rate(
        last.count().subtract(first.count()), last.tick() - first.tick(), ticksPerUnit, decimals);
  }
}
