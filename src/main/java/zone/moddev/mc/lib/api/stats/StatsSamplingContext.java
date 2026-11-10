package zone.moddev.mc.lib.api.stats;

import java.math.BigInteger;
import java.util.IdentityHashMap;
import java.util.Map;

/** One server sample's game time and combined resource demands, matched by identity. */
public final class StatsSamplingContext {
  private final long tick;
  private final Map<Object, BigInteger> demands;

  public StatsSamplingContext(long tick, Map<Object, BigInteger> demands) {
    this.tick = tick;
    this.demands = new IdentityHashMap<>(demands);
  }

  public long tick() {
    return tick;
  }

  public BigInteger demand(Object resource) {
    return demands.getOrDefault(resource, BigInteger.ZERO);
  }
}
