package zone.moddev.mc.lib.gui.layout;

import zone.moddev.mc.lib.gui.model.Text;

/**
 * Measures text for layout. On the client it translates the key and asks the font; at load, where
 * no font exists, {@link #NONE} measures everything as zero width. Text is always one line, so
 * heights never depend on it.
 */
@FunctionalInterface
public interface TextMeasure {
  /** Height of one line of text, as vanilla's font. */
  int LINE_HEIGHT = 9;

  /** Measures every text as zero width; for load-time checks that only need heights. */
  TextMeasure NONE = text -> 0;

  /** The width of {@code text} in GUI pixels, as it will be shown. */
  int width(Text text);
}
