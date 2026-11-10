package zone.moddev.mc.lib.api.stats;

/**
 * A machine part's server-side statistics. Returning an empty section adds no
 * drawer rows.
 */
@FunctionalInterface
public interface StatsProvider {
    Statistics statistics();
}
