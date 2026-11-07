package zone.moddev.mc.lib.gui.runtime;

import java.math.BigInteger;
import java.util.*;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import zone.moddev.mc.lib.api.stats.Statistics;
import zone.moddev.mc.lib.api.stats.StatsProvider;

/** Server snapshots from the public provider contract; no concrete storage/processor knowledge. */
final class ScreenStats {
  static final int KEY = 1 << 21;

  private record Sampled(int window, CounterRate rate) {}

  private record Part(StatsProvider provider, Statistics section) {}

  private final Map<StatsProvider, Map<String, Sampled>> counters = new IdentityHashMap<>();
  private Map<Integer, Object> snapshot = Map.of();
  private long lastTick = Long.MIN_VALUE;

  Map<Integer, Object> read(ScreenHost host, long tick) {
    if (lastTick != Long.MIN_VALUE && tick >= lastTick && tick - lastTick < 20) return snapshot;
    return readParts(host == null ? List.of() : host.screenStats(), tick);
  }

  Map<Integer, Object> readParts(List<? extends StatsProvider> providers, long tick) {
    lastTick = tick;
    var seen = Collections.newSetFromMap(new IdentityHashMap<StatsProvider, Boolean>());
    var parts = new ArrayList<Part>();
    var demands = new IdentityHashMap<Object, BigInteger>();
    for (var provider : providers) {
      if (!seen.add(provider)) continue;
      var section = provider.statistics();
      parts.add(new Part(provider, section));
      for (var use : section.maximumUses())
        demands.merge(use.resource(), use.perTick(), BigInteger::add);
      var samples = counters.computeIfAbsent(provider, ignored -> new HashMap<>());
      var currentIds = new HashSet<String>();
      for (var row : section.rows())
        if (row.value() instanceof Statistics.Counter counter) {
          currentIds.add(row.id());
          var sampled = samples.get(row.id());
          if (sampled == null || sampled.window() != counter.windowTicks()) {
            sampled = new Sampled(counter.windowTicks(), new CounterRate(counter.windowTicks()));
            samples.put(row.id(), sampled);
          }
          sampled.rate().sample(counter.total(), tick);
        }
      samples.keySet().retainAll(currentIds);
    }
    counters.keySet().retainAll(seen);
    var result = new HashMap<Integer, Object>();
    int[] units = {1, 20, 1200};
    String[] names = {"tick", "second", "minute"};
    for (int unit = 0; unit < units.length; unit++) {
      var lines = new ArrayList<Component>();
      for (var part : parts) {
        var section = part.section();
        var visible =
            section.rows().stream()
                .filter(
                    row ->
                        row.demandResource() == null
                            || demands.getOrDefault(row.demandResource(), BigInteger.ZERO).signum()
                                > 0)
                .toList();
        if (visible.isEmpty()) continue;
        if (!lines.isEmpty()) lines.add(Component.empty());
        if (!section.title().equals(Component.empty()))
          lines.add(section.title().copy().withStyle(ChatFormatting.BOLD));
        for (var row : visible) {
          var line = row.label().copy().append(": ");
          if (row.value() instanceof Statistics.Text text) line.append(text.text().copy());
          else {
            int multiplier = row.perTimescale() ? units[unit] : 1;
            String number =
                switch (row.value()) {
                  case Statistics.Counter ignored ->
                      counters
                          .get(part.provider())
                          .get(row.id())
                          .rate()
                          .format(multiplier, row.decimals());
                  case Statistics.Rate rate ->
                      StatsNumbers.rate(rate.amount(), rate.ticks(), multiplier, row.decimals());
                  case Statistics.MaximumUse use ->
                      StatsNumbers.rate(
                          demands.getOrDefault(use.resource(), BigInteger.ZERO),
                          1,
                          multiplier,
                          row.decimals());
                  default -> throw new IllegalStateException("Unsupported statistics value");
                };
            line.append(number).append(" ").append(row.unit().copy());
            if (row.perTimescale()) line.append("/").append(Statistics.label(names[unit]));
          }
          lines.add(line);
        }
      }
      if (lines.isEmpty()) lines.add(Statistics.label("empty"));
      if (lines.size() > 4096) throw new IllegalArgumentException("Too many statistics rows");
      result.put(KEY + unit, List.copyOf(lines));
    }
    return snapshot = Map.copyOf(result);
  }
}
