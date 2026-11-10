package zone.moddev.mc.lib.api.stats;

import java.util.Objects;

import net.minecraft.network.chat.Component;

/** Row formatting options; custom statistics may use these helpers or return their own text. */
public record StatsFormatContext(
    StatsTimescale timescale, Component unit, int decimals, boolean perTimescale) {
    public StatsFormatContext {
        Objects.requireNonNull(timescale);
        unit = Objects.requireNonNull(unit).copy();
        if (decimals < 0 || decimals > 8)
            throw new IllegalArgumentException("Decimal precision");
    }

    public Component unit() {
        return unit.copy();
    }

    public int ticks() {
        return perTimescale ? timescale.ticks() : 1;
    }

    /** Standard numeric value plus row unit and optional timescale suffix. */
    public Component number(String number) {
        var result = Component.literal(number).append(" ").append(unit());
        if (perTimescale)
            result.append("/").append(timescale.unit());
        return result;
    }
}
