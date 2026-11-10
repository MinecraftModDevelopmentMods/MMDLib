package zone.moddev.mc.lib.gui.drawer;

import java.util.Objects;

import zone.moddev.mc.lib.gui.model.Edge;

/** Shared tab geometry. Smaller order values sit closest to the chosen anchor. */
public record DrawerPlacement(
    Edge edge, Anchor anchor, int order, int width, int height, int overlap, int gap) {
    public static final int DEFAULT_WIDTH = 22, DEFAULT_HEIGHT = 24, DEFAULT_GAP = 2;

    public enum Anchor {
        TOP, BOTTOM
    }

    public DrawerPlacement {
        Objects.requireNonNull(edge);
        Objects.requireNonNull(anchor);
        if (width < 1
            || height < 1
            || width > 8192
            || height > 8192
            || overlap < 0
            || overlap >= width
            || gap < 0
            || gap > 8192)
            throw new IllegalArgumentException("Invalid drawer tab geometry");
    }

    public static DrawerPlacement top(Edge edge, int order) {
        return new DrawerPlacement(
            edge, Anchor.TOP, order, DEFAULT_WIDTH, DEFAULT_HEIGHT, 0, DEFAULT_GAP);
    }

    public static DrawerPlacement bottom(Edge edge, int order) {
        return new DrawerPlacement(edge, Anchor.BOTTOM, order, DEFAULT_WIDTH, DEFAULT_HEIGHT, 0, 0);
    }
}
