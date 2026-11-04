package zone.moddev.mc.lib.machine.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EnergyScheduleTest {
  private static final long[][] CASES = {
    {4000, 200}, {100, 3}, {1, 200}, {0, 5}, {10_000_000_000L, 7}, {Long.MAX_VALUE, 1_000_003}
  };

  @Test
  void sharesAddUpToTheTotal() {
    for (var c : CASES) {
      int time = (int) c[1];
      long sum = 0;
      for (int tick = 0; tick < time; tick++) sum += EnergySchedule.share(c[0], time, tick);
      assertEquals(c[0], sum, c[0] + " over " + time);
    }
  }

  @Test
  void neverAboveTheStartingCheck() {
    for (var c : CASES) {
      int time = (int) c[1];
      long most = Math.ceilDiv(c[0], (long) time);
      for (int tick = 0; tick < time; tick++)
        assertTrue(EnergySchedule.share(c[0], time, tick) <= most, c[0] + " over " + time);
    }
  }

  @Test
  void crusherIsEven() {
    for (int tick = 0; tick < 200; tick++) assertEquals(20, EnergySchedule.share(4000, 200, tick));
  }
}
