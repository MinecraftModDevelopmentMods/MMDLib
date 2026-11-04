package zone.moddev.mc.lib.machine.feature;

/** How a cycle spreads its energy over its ticks. */
final class EnergySchedule {
  private EnergySchedule() {}

  /**
   * The energy to draw on tick {@code tick} (from 0) of a cycle of {@code time} ticks that needs
   * {@code total} in all. The shares add up to {@code total}, and none is above {@code
   * ceil(total / time)}, so a check of the first tick's share is enough to start.
   */
  static long share(long total, int time, int tick) {
    long base = total / time;
    long rest = total % time;
    return base + (tick + 1L) * rest / time - tick * rest / time;
  }
}
