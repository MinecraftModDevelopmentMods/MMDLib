package zone.moddev.mc.lib.util.exceptions;

public class ParseException extends RuntimeException {
    public ParseException(final String message) {
        super(message);
    }

    public ParseException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
