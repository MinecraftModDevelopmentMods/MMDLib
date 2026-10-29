package zone.moddev.mc.lib.util.parse;

import com.google.gson.JsonParseException;

/** A JSON value had the wrong type or failed a check; the message names its path. */
public class JParseException extends JsonParseException {
  /** An error with {@code message}. */
  public JParseException(String message) {
    super(message);
  }

  /** An error with {@code message}, caused by {@code cause}. */
  public JParseException(String message, Throwable cause) {
    super(message, cause);
  }

  /** An error caused by {@code cause}. */
  public JParseException(Throwable cause) {
    super(cause);
  }
}
