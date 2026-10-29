package zone.moddev.mc.lib.util.exceptions;

/** Invalid definition data found while parsing. */
public class ParseException extends RuntimeException {
  /** An error with {@code message}. */
  public ParseException(final String message) {
    super(message);
  }

  /** An error with {@code message}, caused by {@code cause}. */
  public ParseException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
