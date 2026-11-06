package zone.moddev.mc.lib.gui.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class EquipmentDiscoveryTest {
  @Test
  void sharesOneScanPerTickAndRefreshesImmediatelyAfterInvalidation() {
    long[] tick = {1};
    int[] scans = {0};
    var cache =
        new EquippedContainers.Discovery(
            () -> tick[0],
            () -> {
              scans[0]++;
              return new EquippedContainers.Snapshot(List.of(), List.of());
            });
    var first = cache.get();
    assertSame(first, cache.get());
    assertEquals(1, scans[0]);
    tick[0]++;
    assertNotSame(first, cache.get());
    assertEquals(2, scans[0]);
    cache.invalidate();
    cache.get();
    cache.get();
    assertEquals(3, scans[0]);
  }
}
