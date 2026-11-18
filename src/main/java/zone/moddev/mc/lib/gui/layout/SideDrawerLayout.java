package zone.moddev.mc.lib.gui.layout;

/**
 * Shared panel insets and bounds for drawers with independent content and tab
 * placement.
 */
public final class SideDrawerLayout {
    public static final int MARGIN = 4;

    /**
     * Content drawers can grow to the viewport; inventory drawers stay aligned
     * with the panel.
     */
    public enum HeightLimit {
        PANEL, SCREEN
    }

    /** Horizontal fitting is a policy; panel-top anchoring is always shared. */
    public enum WidthPolicy {
        OUTWARD, INWARD_IF_NEEDED
    }

    private SideDrawerLayout() {
    }

    public static int bodyX(zone.moddev.mc.lib.gui.model.Edge edge, Rect tab,
        int width) {
        return edge == zone.moddev.mc.lib.gui.model.Edge.LEFT
            ? tab.x() - width
            : tab.right();
    }

    public static int room(zone.moddev.mc.lib.gui.model.Edge edge, Rect tab,
        int screenWidth) {
        return Math.max(0,
            edge == zone.moddev.mc.lib.gui.model.Edge.LEFT
                ? tab.x() - MARGIN
                : screenWidth - tab.right() - MARGIN);
    }

    public static int top(Rect panel) {
        return Math.max(MARGIN, panel.y() + MARGIN);
    }

    /** Standard content anchor: tab order never changes the body's top edge. */
    public static Rect topBody(Rect panel, int screenBottom,
        zone.moddev.mc.lib.gui.model.Edge edge, Rect tab, int width,
        int desiredHeight, HeightLimit limit) {
        return topBody(panel, Integer.MAX_VALUE, screenBottom, edge, tab, width,
            desiredHeight, limit, WidthPolicy.OUTWARD);
    }

    public static Rect topBody(Rect panel, int screenWidth, int screenBottom,
        zone.moddev.mc.lib.gui.model.Edge edge, Rect tab, int width,
        int desiredHeight, HeightLimit limit, WidthPolicy fitting) {
        int x = bodyX(edge, tab, width);
        if (fitting == WidthPolicy.INWARD_IF_NEEDED) {
            int right = Math.max(MARGIN, screenWidth - MARGIN);
            int leftEnd = Math.clamp(tab.x(), MARGIN, right);
            int rightStart = Math.clamp(tab.right(), MARGIN, right);
            int leftRoom = leftEnd - MARGIN, rightRoom = right - rightStart;
            boolean left = edge == zone.moddev.mc.lib.gui.model.Edge.LEFT;
            if ((left ? leftRoom : rightRoom) < width
                && (left ? rightRoom > leftRoom : leftRoom > rightRoom))
                left = !left;
            width = Math.min(width, left ? leftRoom : rightRoom);
            x = left ? leftEnd - width : rightStart;
        }
        return body(panel, screenBottom, x, top(panel), width,
            Math.max(desiredHeight, tab.bottom() - top(panel)), limit);
    }

    /** The screen/overlay limit already includes its own margin. */
    public static int bottom(Rect panel, int screenBottom) {
        return Math.min(panel.bottom() - MARGIN, screenBottom);
    }

    /**
     * Fits a content-sized or full-height body between the shared panel and
     * screen limits.
     */
    public static Rect body(Rect panel, int screenBottom, int x, int y,
        int width, int desiredHeight) {
        return body(panel, screenBottom, x, y, width, desiredHeight,
            HeightLimit.PANEL);
    }

    public static Rect body(Rect panel, int screenBottom, int x, int y,
        int width, int desiredHeight, HeightLimit limit) {
        int top = Math.max(top(panel), y);
        int bottom = limit == HeightLimit.SCREEN
            ? screenBottom
            : bottom(panel, screenBottom);
        int available = Math.max(0, bottom - top);
        return new Rect(x, top, width,
            Math.min(Math.max(0, desiredHeight), available));
    }

    public static Rect rail(Rect tab, Rect body) {
        return new Rect(tab.x(), body.y(), tab.width(), body.height());
    }

    /** Shared background beneath the content and the whole column of tabs. */
    public static Rect frame(Rect tab, Rect body) {
        int left = Math.min(tab.x(), body.x());
        return new Rect(left, body.y(),
            Math.max(tab.right(), body.right()) - left, body.height());
    }
}
