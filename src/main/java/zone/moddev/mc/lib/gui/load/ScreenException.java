package zone.moddev.mc.lib.gui.load;

/** A screen file is invalid. The message names the file and, where known, the line. */
public class ScreenException extends IllegalArgumentException {
  /** An error at {@code line} of {@code source}; a line below 1 means no line is known. */
  public ScreenException(String source, int line, String message) {
    super(line > 0 ? source + ":" + line + ": " + message : source + ": " + message);
  }

  /** An error at {@code line} of {@code source}, caused by {@code cause}. */
  public ScreenException(String source, int line, String message, Throwable cause) {
    super(line > 0 ? source + ":" + line + ": " + message : source + ": " + message, cause);
  }
}
