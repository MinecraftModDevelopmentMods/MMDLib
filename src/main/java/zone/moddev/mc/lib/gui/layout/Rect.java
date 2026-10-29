package zone.moddev.mc.lib.gui.layout;

/**
 * A rectangle in GUI pixels, relative to the main panel's top-left corner.
 *
 * @param x left edge
 * @param y top edge
 * @param width width
 * @param height height
 */
public record Rect(int x, int y, int width, int height) {
  /** The right edge (exclusive). */
  public int right() {
    return x + width;
  }

  /** The bottom edge (exclusive). */
  public int bottom() {
    return y + height;
  }

  /** Whether the point is inside. */
  public boolean contains(double px, double py) {
    return px >= x && px < right() && py >= y && py < bottom();
  }

  /** This rectangle moved by {@code dx, dy}. */
  public Rect offset(int dx, int dy) {
    return new Rect(x + dx, y + dy, width, height);
  }
}
