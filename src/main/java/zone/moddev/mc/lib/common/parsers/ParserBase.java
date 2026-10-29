package zone.moddev.mc.lib.common.parsers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Base of the older JSON parsers kept for compatibility. Decoding is done by the DFU codecs in
 * {@code common.codecs}; the parsers wrap them and return builders.
 *
 * @param <I> the parse result
 * @param <B> the builder type
 */
public abstract class ParserBase<I, B> {
  protected JsonObject baseData;
  protected B builder;

  /** Supplies the empty builder a parser starts from. */
  public interface BuilderGetter<T> {
    /** The empty builder. */
    public T run();
  }

  /** A parser over {@code data}. */
  public ParserBase(JsonObject data, BuilderGetter<B> getter) {
    baseData = data;
    builder = getter.run();
  }

  /**
   * Parses the whole object.
   *
   * @throws zone.moddev.mc.lib.util.exceptions.ParseException if the data is invalid
   */
  public abstract I parse();

  /** Applies one key; kept for older subclasses. */
  protected abstract B parseKey(final String key);

  /** Whether {@code elem} is a non-empty string. */
  protected final boolean validateValueIsString(JsonElement elem) {
    return elem.isJsonPrimitive() && (elem.getAsString().length() > 0);
  }

  /** {@code elem} as a string. */
  protected final String getAsString(JsonElement elem) {
    return elem.getAsString();
  }
}
