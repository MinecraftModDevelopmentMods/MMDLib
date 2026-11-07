package zone.moddev.mc.lib.gui.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.stats.Statistics;
import zone.moddev.mc.lib.api.stats.StatsProvider;

class ScreenStatsTest {
  @Test
  void transferMaximaFollowTheSelectedTimescale() {
    var energy = new EnergyBuffer(10000, 1000, 500);
    var values = new ScreenStats().readParts(List.of(energy), 0);
    String[] input = {": 1,000 RF/", ": 20,000 RF/", ": 1,200,000 RF/"};
    String[] output = {": 500 RF/", ": 10,000 RF/", ": 600,000 RF/"};
    for (int unit = 0; unit < 3; unit++) {
      var rows = lines(values, unit);
      assertTrue(rows.get(3).contains(input[unit]));
      assertTrue(rows.get(4).contains(output[unit]));
      assertFalse(rows.get(3).contains("transfer"));
    }
  }

  @Test
  void readsCustomProvidersOnceAndTracksRowsWithoutConcreteTypes() {
    var count = new AtomicReference<>(BigInteger.ZERO);
    var calls = new AtomicInteger();
    StatsProvider part =
        () -> {
          calls.incrementAndGet();
          return new Statistics(
              Component.literal("Custom part"),
              List.of(
                  new Statistics.Row(
                      "work",
                      Component.literal("Work"),
                      new Statistics.Counter(count.get(), 20),
                      Component.literal("units"),
                      2,
                      true)));
        };
    var stats = new ScreenStats();
    stats.readParts(List.of(part), 0);
    count.set(BigInteger.valueOf(1500));
    var values = stats.readParts(List.of(part), 20);
    assertEquals(2, calls.get());
    assertEquals("Custom part", lines(values, 1).getFirst());
    assertTrue(lines(values, 1).get(1).contains("1,500.00 units/"));
  }

  @Test
  void separatesInputOutputAndProcessingUseAndRestoresAllOnRollback() {
    var energy = new EnergyBuffer(10000, 1000, 500);
    var parts = List.of(energy, demand(energy, 20));
    var stats = new ScreenStats();
    stats.readParts(parts, 0);
    energy.insert(1000, false);
    energy.extract(100, false);
    assertTrue(energy.consume(400));
    var undo = energy.checkpoint();
    energy.insert(30, false);
    energy.extract(40, false);
    energy.consume(50);
    undo.run();
    var rows = lines(stats.readParts(parts, 20), 1);
    assertEquals(7, rows.size());
    assertTrue(rows.get(1).contains(": 1,000 RF/"));
    assertTrue(rows.get(2).contains(": 100 RF/"));
    assertTrue(rows.get(5).contains(": 400 RF/"));
  }

  @Test
  void usageRowsRequireCurrentDemandForThatBuffer() {
    var energy = new EnergyBuffer(10000, 0, 1000);
    var other = new EnergyBuffer(10000, 1000, 0);
    var stats = new ScreenStats();
    assertEquals(3, lines(stats.readParts(List.of(energy), 0), 0).size());
    assertEquals(3, lines(stats.readParts(List.of(energy, demand(other, 20)), 20), 0).size());
    assertEquals(3, lines(stats.readParts(List.of(energy, demand(energy, 0)), 40), 0).size());
    assertEquals(5, lines(stats.readParts(List.of(energy, demand(energy, 20)), 60), 0).size());
    energy.setStored(100);
    energy.consume(20);
    assertEquals(3, lines(stats.readParts(List.of(energy), 80), 0).size());
  }

  private static StatsProvider demand(Object resource, long perTick) {
    return () ->
        new Statistics(
            Component.empty(),
            List.of(),
            List.of(new Statistics.EnergyUse(resource, BigInteger.valueOf(perTick))));
  }

  @Test
  void aggregatesOnlyMatchingProcessorResourcesRegardlessOfOrder() {
    var first = new EnergyBuffer(10000, 1000, 0);
    var second = new EnergyBuffer(10000, 0, 1000);
    StatsProvider a =
        () ->
            new Statistics(
                Component.empty(),
                List.of(),
                List.of(new Statistics.EnergyUse(first, BigInteger.valueOf(20))));
    StatsProvider b =
        () ->
            new Statistics(
                Component.empty(),
                List.of(),
                List.of(
                    new Statistics.EnergyUse(first, BigInteger.valueOf(7)),
                    new Statistics.EnergyUse(second, BigInteger.valueOf(3))));
    var stats = new ScreenStats();
    var rows = lines(stats.readParts(List.of(first, a, second, b, a), 0), 2);
    assertEquals(11, rows.size()); // two four-row sections, their headers and one separator
    assertTrue(rows.get(4).contains(": 32,400 RF/"));
    assertTrue(rows.get(10).contains(": 3,600 RF/"));
    assertEquals(
        List.of("in", "in_max", "used", "used_max"),
        first.statistics().rows().stream().map(Statistics.Row::id).toList());
    assertEquals(
        List.of("out", "out_max", "used", "used_max"),
        second.statistics().rows().stream().map(Statistics.Row::id).toList());
  }

  @Test
  void emptyPartsAddNothingAndMissingRecipesHaveNoInventedMaximum() {
    StatsProvider empty = () -> Statistics.EMPTY;
    StatsProvider processor =
        () ->
            new Statistics(
                Component.literal("Processor A"),
                List.of(
                    Statistics.cycles(
                        "cycles_max", new Statistics.Text(Component.literal("No recipe")))));
    var rows = lines(new ScreenStats().readParts(List.of(empty, processor), 0), 2);
    assertEquals(2, rows.size());
    assertTrue(rows.get(1).endsWith(": No recipe"));
    assertFalse(rows.get(1).contains("cycles/"));
  }

  private static List<String> lines(java.util.Map<Integer, Object> values, int unit) {
    return ((List<?>) values.get(ScreenStats.KEY + unit))
        .stream().map(Component.class::cast).map(Component::getString).toList();
  }
}
