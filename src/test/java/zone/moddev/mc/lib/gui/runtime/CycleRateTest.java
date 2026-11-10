package zone.moddev.mc.lib.gui.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.stats.CounterRate;

class CycleRateTest {
  @Test
  void roundsToTwoDecimalsBeforeGrouping() {
    var rate = new CounterRate(1200);
    rate.sample(BigInteger.ZERO, 0);
    rate.sample(BigInteger.valueOf(199999), 200);
    assertEquals("1,000.00", rate.format(1, 2));
    assertEquals("19,999.90", rate.format(20, 2));
    assertEquals("1,199,994.00", rate.format(1200, 2));
  }

  @Test
  void averagesCompletionsAndConvertsUnits() {
    var rate = new CounterRate(1200);
    rate.sample(BigInteger.valueOf(50), 0);
    assertEquals("—", rate.format(20, 2));
    rate.sample(BigInteger.valueOf(51), 100);
    assertEquals("0.01", rate.format(1, 2));
    assertEquals("0.20", rate.format(20, 2));
    assertEquals("12.00", rate.format(1200, 2));
    rate.sample(BigInteger.valueOf(51), 200);
    assertEquals("6.00", rate.format(1200, 2));
  }

  @Test
  void expiresActivityAndKeepsProcessorsIndependent() {
    var first = new CounterRate(1200);
    var second = new CounterRate(1200);
    for (int tick = 0; tick <= 1200; tick += 20) {
      first.sample(BigInteger.valueOf(tick / 100), tick);
      second.sample(BigInteger.valueOf(tick / 200), tick);
    }
    assertEquals("12.00", first.format(1200, 2));
    assertEquals("6.00", second.format(1200, 2));
    for (int tick = 1220; tick <= 2400; tick += 20) first.sample(BigInteger.valueOf(12), tick);
    assertEquals("0.00", first.format(1200, 2));
  }

  @Test
  void resetsBaselineWhenClockOrCounterRestarts() {
    var rate = new CounterRate(1200);
    rate.sample(BigInteger.TEN, 100);
    rate.sample(BigInteger.ZERO, 120);
    assertEquals("—", rate.format(1, 2));
    rate.sample(BigInteger.ONE, 20);
    assertEquals("—", rate.format(1, 2));
  }
}
