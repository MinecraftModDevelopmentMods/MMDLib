package zone.moddev.mc.lib.util.exceptions;

/** Invalid item definition data. */
public class ItemParseException extends ParseException {
  /** An error with {@code message}. */
  public ItemParseException(String message) {
    super(message);
  }

  /** An error with {@code message}, caused by {@code cause}. */
  public ItemParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
