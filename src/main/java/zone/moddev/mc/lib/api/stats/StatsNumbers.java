package zone.moddev.mc.lib.api.stats;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import java.text.NumberFormat;

import java.util.Locale;

/** Exact rate conversion with fixed precision and comma grouping, independent of server locale. */
public final class StatsNumbers {
    private StatsNumbers() {
    }

    public static String rate(BigInteger amount, long elapsed, int ticksPerUnit, int decimals) {
        var value = new BigDecimal(amount)
            .multiply(BigDecimal.valueOf(ticksPerUnit))
            .divide(BigDecimal.valueOf(elapsed), decimals, RoundingMode.HALF_UP);
        var format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(decimals);
        format.setMaximumFractionDigits(decimals);
        return format.format(value);
    }
}
