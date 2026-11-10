package zone.moddev.mc.lib.gui.layout;

/** Compact built-in redstone controls beneath the shared face grid. */
public final class RedstoneLayout {
    public static final int WIDTH = AutomationLayout.WIDTH, CONTROLS_TOP = 73;

    private RedstoneLayout() {
    }

    public static Rect face(int face) {
        var original = AutomationLayout.face(face);
        return new Rect(27 + (original.x() - 36) / 18 * 20,
            7 + (original.y() - 7) / 18 * 20, 18, 18);
    }

    public static Rect intervalMode() {
        return new Rect(5, 86, 40, 16);
    }

    public static Rect range() {
        return new Rect(47, 75, 60, 37);
    }

    public static Rect bar() {
        return new Rect(50, 91, 54, 5);
    }

    public static int signalX(Rect bar, int value) {
        return bar.x() + Math.round((value + .5f) * bar.width() / 16);
    }

    public static int signalAt(Rect bar, double x) {
        return Math.clamp(
            (int) Math.round((x - bar.x()) * 16 / bar.width() - .5), 0, 15);
    }

    public static Rect revert() {
        return new Rect(5, 77, 18, 18);
    }

    public static Rect group() {
        return new Rect(27, 77, 80, 18);
    }
}
