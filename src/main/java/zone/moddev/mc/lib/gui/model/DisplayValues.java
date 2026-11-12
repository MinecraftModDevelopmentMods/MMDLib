package zone.moddev.mc.lib.gui.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Numeric defaults used by element presentation hooks; resource interpretation
 * belongs to elements.
 */
public final class DisplayValues {
    private DisplayValues() {
    }

    public static String number(Object value, boolean full) {
        if (value == null)
            return "?";
        if (value instanceof BigInteger n && !full) {
            String[] units = {"", " k", " M", " G", " T", " P", " E", " Z",
                " Y"};
            int exponent = Math.max(0, (n.abs().toString().length() - 1) / 3);
            if (exponent >= units.length)
                return new BigDecimal(n).round(new MathContext(3))
                    .stripTrailingZeros().toEngineeringString();
            if (exponent > 0)
                return new BigDecimal(n)
                    .divide(BigDecimal.TEN.pow(exponent * 3), 1,
                        RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString() + units[exponent];
        }
        return value.toString();
    }

    public static double ratio(Object value, Object maximum) {
        if (!(value instanceof Number a) || !(maximum instanceof Number b))
            return 0;
        try {
            BigDecimal numerator = decimal(a), denominator = decimal(b);
            return denominator.signum() <= 0
                ? 0
                : Math.clamp(numerator
                    .divide(denominator, 8, RoundingMode.HALF_UP).doubleValue(),
                    0, 1);
        } catch (ArithmeticException | NumberFormatException invalid) {
            return 0;
        }
    }

    private static BigDecimal decimal(Number value) {
        if (value instanceof BigDecimal n)
            return n;
        if (value instanceof BigInteger n)
            return new BigDecimal(n);
        if (value instanceof Byte || value instanceof Short
            || value instanceof Integer || value instanceof Long)
            return BigDecimal.valueOf(value.longValue());
        return BigDecimal.valueOf(value.doubleValue());
    }
}
