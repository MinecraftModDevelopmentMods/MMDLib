package zone.moddev.mc.lib.api.stats;

import java.util.List;
import java.util.Objects;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Data supplied by a host, without registration. Labels remain translatable on
 * the client.
 */
public record StatsTimescale(ResourceLocation id, int ticks, Component label,
    Component unit) {
    public static final int MAX_TIMESCALES = 16;
    public static final StatsTimescale TICK = builtin("tick", 1);
    public static final StatsTimescale SECOND = builtin("second", 20);
    public static final StatsTimescale MINUTE = builtin("minute", 1200);
    public static final List<StatsTimescale> DEFAULTS = List.of(TICK, SECOND,
        MINUTE);

    public StatsTimescale {
        Objects.requireNonNull(id);
        if (ticks <= 0)
            throw new IllegalArgumentException(
                "Timescale ticks must be positive");
        label = Objects.requireNonNull(label).copy();
        unit = Objects.requireNonNull(unit).copy();
    }

    public Component label() {
        return label.copy();
    }

    public Component unit() {
        return unit.copy();
    }

    private static StatsTimescale builtin(String name, int ticks) {
        return new StatsTimescale(ResourceLocation.parse("mmdlib:" + name),
            ticks, Statistics.label("per_" + name), Statistics.label(name));
    }

    /** Empty lists are allowed; the drawer then has no selectable pages. */
    public static List<StatsTimescale> checked(
        List<StatsTimescale> timescales) {
        var result = List.copyOf(timescales);
        if (result.size() > MAX_TIMESCALES || result.stream()
            .map(StatsTimescale::id).distinct().count() != result.size())
            throw new IllegalArgumentException(
                "Too many statistics timescales or duplicate IDs");
        return result;
    }
}
