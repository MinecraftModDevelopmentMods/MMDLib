package zone.moddev.mc.lib.gui.layout;

import zone.moddev.mc.lib.gui.model.Edge;

/**
 * Keeps complete recipe rows at native scale, overlapping the host when the
 * side margin is narrow.
 */
public final class RecipeDrawerLayout {
    public static final int HEADER = 24, FOOTER = 22, INSET = 6;

    private RecipeDrawerLayout() {
    }

    public static Rect body(Edge edge, Rect tab, Rect panel, int screenWidth,
        int screenBottom, int recipeWidth, int rowHeight) {
        int width = Math.max(160, recipeWidth) + 2 * INSET;
        int right = Math.max(SideDrawerLayout.MARGIN,
            screenWidth - SideDrawerLayout.MARGIN);
        int leftEnd = Math.clamp(tab.x(), SideDrawerLayout.MARGIN, right);
        int rightStart = Math.clamp(tab.right(), SideDrawerLayout.MARGIN,
            right);
        int leftRoom = leftEnd - SideDrawerLayout.MARGIN;
        int rightRoom = right - rightStart;
        boolean left = edge == Edge.LEFT;
        // Open inward when the outer margin cannot fit the recipes. Never
        // clamp the body across the fixed navigation rail.
        if ((left ? leftRoom : rightRoom) < width
            && (left ? rightRoom > leftRoom : leftRoom > rightRoom))
            left = !left;
        width = Math.min(width, left ? leftRoom : rightRoom);
        int x = left ? leftEnd - width : rightStart;
        return SideDrawerLayout.body(panel, screenBottom, x,
            SideDrawerLayout.top(panel), width, panel.height());
    }

    public static Rect content(Rect body) {
        return new Rect(body.x() + INSET, body.y() + HEADER,
            Math.max(0, body.width() - 2 * INSET),
            Math.max(0, body.height() - HEADER - FOOTER));
    }

    public static int pageSize(Rect body, int rowHeight) {
        if (rowHeight <= 0)
            throw new IllegalArgumentException(
                "Recipe row height must be positive");
        return Math.max(1, content(body).height() / rowHeight);
    }
}
