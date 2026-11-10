package zone.moddev.mc.lib.gui.model;

/**
 * A {@code shows} target: {@code context:view}.
 *
 * @param context the context area's ID
 * @param view the view's ID
 */
public record ViewRef(String context, String view) {
  /** Parses {@code context:view}. */
  public static ViewRef parse(String text) {
    int colon = text.indexOf(':');
    if (colon <= 0 || colon == text.length() - 1)
      throw new IllegalArgumentException("Expected context:view: " + text);
    return new ViewRef(text.substring(0, colon), text.substring(colon + 1));
  }

  @Override
  public String toString() {
    return context + ":" + view;
  }
}
