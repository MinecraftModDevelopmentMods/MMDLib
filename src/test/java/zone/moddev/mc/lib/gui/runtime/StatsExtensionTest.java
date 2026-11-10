package zone.moddev.mc.lib.gui.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.*;

import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.stats.*;
import zone.moddev.mc.lib.gui.sync.ElementStateValue;

class StatsExtensionTest {
  private static final StatsTimescale HOUR =
      new StatsTimescale(
          ResourceLocation.parse("test:hour"),
          72000,
          Component.literal("Per hour"),
          Component.literal("h"));

  private record History(int calls, int amount) implements Statistics.Sample {
    public Component format(StatsFormatContext c) {
      return Component.literal(calls + ":" + amount + ":" + c.ticks())
          .withStyle(ChatFormatting.GOLD);
    }
  }

  private record Reading(int config, int amount, AtomicInteger samples)
      implements Statistics.Value {
    public Object samplingKey() {
      return config;
    }

    public Statistics.Sample sample(StatsSamplingContext context, Statistics.Sample previous) {
      samples.incrementAndGet();
      return new History(previous == null ? 1 : ((History) previous).calls() + 1, amount);
    }
  }

  private static Statistics.Row row(String id, Statistics.Value value) {
    return new Statistics.Row(
        id, Component.literal(id), value, Component.literal("units"), 0, true);
  }

  @Test
  void customTypesSampleOnceAndFormatAllHostTimescales() {
    var calls = new AtomicInteger();
    StatsProvider provider =
        () ->
            new Statistics(
                Component.empty(),
                List.of(
                    row("custom", new Reading(1, 7, calls)),
                    row("rate", new Statistics.Rate(BigInteger.valueOf(2), 1))));
    ScreenHost host =
        new ScreenHost() {
          public net.neoforged.neoforge.items.IItemHandlerModifiable screenItems() {
            return null;
          }

          public List<? extends StatsProvider> screenStats() {
            return List.of(provider, provider);
          }

          public List<StatsTimescale> screenStatsTimescales() {
            return List.of(StatsTimescale.TICK, StatsTimescale.SECOND, StatsTimescale.MINUTE, HOUR);
          }
        };
    var stats = ScreenStats.forHost(host);
    var pages = stats.read(host, 0);
    assertSame(pages, ScreenStats.forHost(host).read(host, 1));
    assertEquals(1, calls.get());
    assertEquals(4, pages.size());
    assertEquals(HOUR.id(), pages.getLast().id());
    assertEquals("Per hour", pages.getLast().label().getString());
    assertEquals("custom: 1:7:72000", pages.getLast().lines().getFirst().getString());
    assertEquals("rate: 144,000 units/h", pages.getLast().lines().get(1).getString());
    assertEquals(
        ChatFormatting.GOLD.getColor().intValue(),
        pages
            .getLast()
            .lines()
            .getFirst()
            .getSiblings()
            .getLast()
            .getStyle()
            .getColor()
            .getValue());
    assertEquals(
        "custom: 2:7:72000", stats.read(host, 20).getLast().lines().getFirst().getString());
    assertEquals("custom: 1:7:72000", pages.getLast().lines().getFirst().getString());
  }

  @Test
  void historyFollowsRowIdsAndResetsForConfigurationTypeRemovalAndTimeRollback() {
    var calls = new AtomicInteger();
    var rows =
        new AtomicReference<>(
            List.of(row("a", new Reading(1, 3, calls)), row("b", new Reading(1, 4, calls))));
    StatsProvider provider = () -> new Statistics(Component.empty(), rows.get());
    var stats = new ScreenStats();
    stats.readParts(List.of(provider), 0);
    rows.set(List.of(row("b", new Reading(1, 40, calls)), row("a", new Reading(1, 30, calls))));
    assertEquals(
        "b: 2:40:1",
        stats.readParts(List.of(provider), 20).getFirst().lines().getFirst().getString());
    rows.set(List.of(row("a", new Reading(2, 30, calls))));
    assertEquals(
        "a: 1:30:1",
        stats.readParts(List.of(provider), 40).getFirst().lines().getFirst().getString());
    // Same key but a different implementation must not receive another type's sample.
    Statistics.Value other =
        new Statistics.Value() {
          public Object samplingKey() {
            return 2;
          }

          public Statistics.Sample sample(StatsSamplingContext c, Statistics.Sample previous) {
            assertNull(previous);
            return f -> Component.literal("other");
          }
        };
    rows.set(List.of(row("a", other)));
    stats.readParts(List.of(provider), 60);
    rows.set(List.of(row("a", new Reading(2, 30, calls)), row("b", new Reading(1, 4, calls))));
    var next = stats.readParts(List.of(provider), 80).getFirst().lines();
    assertEquals("a: 1:30:1", next.getFirst().getString());
    assertEquals("b: 1:4:1", next.getLast().getString());
    stats.readParts(List.of(), 100);
    assertEquals(
        "a: 1:30:1",
        stats.readParts(List.of(provider), 120).getFirst().lines().getFirst().getString());
    assertEquals(
        "a: 1:30:1",
        stats.readParts(List.of(provider), 0).getFirst().lines().getFirst().getString());
  }

  @Test
  void providerIdentityAndCounterWindowChangesKeepHistoriesIndependent() {
    var calls = new AtomicInteger();
    record Provider(AtomicInteger calls) implements StatsProvider {
      public Statistics statistics() {
        return new Statistics(Component.empty(), List.of(row("a", new Reading(1, 1, calls))));
      }
    }
    var a = new Provider(calls);
    var b = new Provider(calls);
    assertEquals(a, b);
    var stats = new ScreenStats();
    stats.readParts(List.of(a), 0);
    var pages = stats.readParts(List.of(a, b), 20);
    assertEquals("a: 2:1:1", pages.getFirst().lines().getFirst().getString());
    assertEquals("a: 1:1:1", pages.getFirst().lines().getLast().getString());
    var value = new AtomicReference<>(new Statistics.Counter(BigInteger.ZERO, 20));
    StatsProvider counter =
        () -> new Statistics(Component.empty(), List.of(row("counter", value.get())));
    stats.readParts(List.of(counter), 40);
    value.set(new Statistics.Counter(BigInteger.valueOf(20), 20));
    assertTrue(
        stats
            .readParts(List.of(counter), 60)
            .getFirst()
            .lines()
            .getFirst()
            .getString()
            .contains(": 1 units/"));
    value.set(new Statistics.Counter(BigInteger.valueOf(40), 40));
    assertTrue(
        stats
            .readParts(List.of(counter), 80)
            .getFirst()
            .lines()
            .getFirst()
            .getString()
            .contains(": — units/"));
  }

  @Test
  void timescaleListsAllowEmptyReorderedAndCustomDurationsButRejectInvalidData() {
    var stats = new ScreenStats();
    assertTrue(stats.readParts(List.of(), List.of(), 0).isEmpty());
    var pages = stats.readParts(List.of(), List.of(HOUR, StatsTimescale.TICK), 20);
    assertEquals(
        List.of(HOUR.id(), StatsTimescale.TICK.id()), pages.stream().map(StatsPage::id).toList());
    assertThrows(
        IllegalArgumentException.class, () -> stats.readParts(List.of(), List.of(HOUR, HOUR), 40));
    assertThrows(
        IllegalArgumentException.class,
        () -> new StatsTimescale(HOUR.id(), 0, HOUR.label(), HOUR.unit()));
    var tooMany =
        java.util.stream.IntStream.range(0, StatsTimescale.MAX_TIMESCALES + 1)
            .mapToObj(
                i ->
                    new StatsTimescale(
                        ResourceLocation.parse("test:s" + i),
                        1,
                        Component.empty(),
                        Component.empty()))
            .toList();
    assertThrows(IllegalArgumentException.class, () -> StatsTimescale.checked(tooMany));
  }

  @Test
  void pagesPreserveStylesLabelsAndDefensiveCopies() {
    var label = Component.translatable("test.hour");
    var line = Component.literal("Custom value").withStyle(ChatFormatting.GREEN);
    var page = new StatsPage(HOUR.id(), label, List.of(line));
    label.append(" changed");
    line.append(" changed");
    var field = StatsMenuComponent.PAGES;
    page.lines().getFirst().getSiblings().clear();
    assertEquals("Custom value", page.lines().getFirst().getString());
    assertEquals("test.hour", page.label().getString());
    assertEquals(
        List.of(),
        field.decode(RegistryAccess.EMPTY, field.encode(RegistryAccess.EMPTY, List.of())));
    assertEquals(2, field.version());
  }

  @Test
  void pageTransportRejectsDuplicatesAndOversizedCounts() {
    var field = StatsMenuComponent.PAGES;
    var page = new StatsPage(HOUR.id(), HOUR.label(), List.of(Component.literal("x")));
    assertThrows(
        IllegalArgumentException.class,
        () -> field.encode(RegistryAccess.EMPTY, List.of(page, page)));
    assertThrows(
        IllegalArgumentException.class,
        () -> field.decode(RegistryAccess.EMPTY, new ElementStateValue(new byte[] {17})));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StatsPage(
                HOUR.id(),
                HOUR.label(),
                Collections.nCopies(StatsPage.MAX_ROWS + 1, Component.empty())));
  }
}
