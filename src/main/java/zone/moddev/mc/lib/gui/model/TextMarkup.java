package zone.moddev.mc.lib.gui.model;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Common-side parser for screen text emphasis, independent of fonts and rendering. */
public final class TextMarkup {
  private TextMarkup() {}

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
