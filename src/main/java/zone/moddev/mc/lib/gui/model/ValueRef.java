package zone.moddev.mc.lib.gui.model;

/**
 * A {@code value} or {@code max} attribute: the Java getter a screen element reads, on the server,
 * every tick while the screen is open.
 */
public sealed interface ValueRef {
  /** The reference as written in the screen file. */
  String text();

  /** Parses {@code #method} or {@code fully.qualified.Class#method}. */
  static ValueRef parse(String text) {
    int hash = text.indexOf('#');
    if (hash < 0 || hash != text.lastIndexOf('#') || hash == text.length() - 1)
      throw new IllegalArgumentException("Expected #method or Class#method: " + text);
    return hash == 0
        ? new Instance(text.substring(1))
        : new Static(text.substring(0, hash), text.substring(hash + 1));
  }

  /**
   * {@code #method}: a public no-argument instance method of the screen's block-entity class (or a
   * superclass or interface of it).
   *
   * @param method the method name
   */
  record Instance(String method) implements ValueRef {
    @Override
    public String text() {
      return "#" + method;
    }
  }

  /**
   * {@code Class#method}: a public static method taking the block entity (or a supertype) as its
   * only parameter.
   *
   * @param owner the fully qualified class name
   * @param method the method name
   */
  record Static(String owner, String method) implements ValueRef {
    @Override
    public String text() {
      return owner + "#" + method;
    }
  }
}
