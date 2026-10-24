package zone.moddev.mc.lib.util.exceptions;

public class ItemParseException extends ParseException {
    public ItemParseException(String message) {
        super(message);
    }

    public ItemParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
