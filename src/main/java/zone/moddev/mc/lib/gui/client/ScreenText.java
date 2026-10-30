package zone.moddev.mc.lib.gui.client;

import java.math.*;
import java.util.*;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.gui.model.Text;

/** Translation diagnostics, tooltip numbers, and the screen's emphasis notation. */
public final class ScreenText {
  private static final Set<String> MISSING = new HashSet<>();

  private ScreenText() {}

  public static Component resolve(Text text, String source) {
    if (!Language.getInstance().has(text.key()) && MISSING.add(source + ":" + text.key()))
      MMDLib.LOGGER.warn("Missing screen translation {} in {}", text.key(), source);
    return component(text);
  }

  public static Component component(Text text) {
    return text.fallback()
        .map(f -> Component.translatableWithFallback(text.key(), f))
        .orElseGet(() -> Component.translatable(text.key()));
  }

  public static String number(Object value, boolean full) {
    if (value == null) return "?";
    if (value instanceof net.neoforged.neoforge.fluids.FluidStack fluid)
      return Integer.toString(fluid.getAmount());
    if (value instanceof BigInteger n && !full) {
      String[] units = {"", " k", " M", " G", " T", " P", " E", " Z", " Y"};
      int exponent = Math.max(0, (n.abs().toString().length() - 1) / 3);
      if (exponent >= units.length)
        return new BigDecimal(n)
            .round(new MathContext(3))
            .stripTrailingZeros()
            .toEngineeringString();
      int unit = exponent;
      if (unit > 0)
        return new BigDecimal(n)
                .divide(BigDecimal.TEN.pow(unit * 3), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
            + units[unit];
    }
    return value.toString();
  }

  public static double ratio(Object value, Object max) {
    if (value instanceof net.neoforged.neoforge.fluids.FluidStack f)
      value = BigInteger.valueOf(f.getAmount());
    if (!(value instanceof Number) || !(max instanceof Number)) return 0;
    try {
      BigDecimal a =
          value instanceof BigInteger b
              ? new BigDecimal(b)
              : BigDecimal.valueOf(((Number) value).doubleValue());
      BigDecimal b =
          max instanceof BigInteger n
              ? new BigDecimal(n)
              : BigDecimal.valueOf(((Number) max).doubleValue());
      return b.signum() <= 0
          ? 0
          : Math.clamp(a.divide(b, 8, RoundingMode.HALF_UP).doubleValue(), 0, 1);
    } catch (ArithmeticException | NumberFormatException ex) {
      return 0;
    }
  }

  public static Component markup(String text) {
    var result = Component.empty();
    var buffer = new StringBuilder();
    Style style = Style.EMPTY;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\\' && i + 1 < text.length() && "*/_".indexOf(text.charAt(i + 1)) >= 0) {
        buffer.append(text.charAt(++i));
        continue;
      }
      boolean mark = "*/_".indexOf(c) >= 0;
      boolean opening =
          (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)))
              && i + 1 < text.length()
              && Character.isLetterOrDigit(text.charAt(i + 1));
      boolean closing =
          i > 0
              && Character.isLetterOrDigit(text.charAt(i - 1))
              && (i + 1 == text.length() || !Character.isLetterOrDigit(text.charAt(i + 1)));
      boolean active =
          c == '*' ? style.isBold() : c == '/' ? style.isItalic() : style.isUnderlined();
      if (mark && ((opening && !active && text.indexOf(c, i + 1) > i) || (closing && active))) {
        result.append(Component.literal(buffer.toString()).setStyle(style));
        buffer.setLength(0);
        style =
            c == '*'
                ? style.withBold(!active)
                : c == '/' ? style.withItalic(!active) : style.withUnderlined(!active);
      } else buffer.append(c);
    }
    return result.append(Component.literal(buffer.toString()).setStyle(style));
  }
}
