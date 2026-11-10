package zone.moddev.mc.companion.client;

import net.minecraft.client.Minecraft;

import zone.moddev.mc.companion.CompanionWidgets;
import zone.moddev.mc.lib.gui.client.WidgetAccess;
import zone.moddev.mc.lib.gui.client.WidgetInstance;
import zone.moddev.mc.lib.gui.layout.Rect;

/**
 * Sends a structured lane-and-state action, validated against this element's
 * configured lane.
 */
final class LaneSelector implements WidgetInstance {
    private final WidgetAccess access;
    private boolean focused, unconfirmed;

    LaneSelector(WidgetAccess access) {
        this.access = access;
    }

    private Rect option(boolean paused) {
        var r = access.bounds();
        int w = (r.width() - 4) / 2;
        return new Rect(r.x() + (paused ? w + 4 : 0), r.y() + 12, w, 18);
    }

    private void choose(boolean paused) {
        if (!access.pending())
            access.submit(CompanionWidgets.SET_LANE,
                new CompanionWidgets.LaneSetting(
                    Integer.parseInt(access.element().properties().get("lane")),
                    paused));
    }

    @Override
    public void render(Context context) {
        var r = context.bounds();
        context.graphics().drawString(Minecraft.getInstance().font,
            WidgetDrawing.text(unconfirmed ? "unconfirmed" : "lane_state"),
            r.x(), r.y(), 0xff404040, false);
        boolean paused = Boolean.TRUE.equals(access.value());
        WidgetDrawing.button(context.graphics(), option(false), "run", !paused,
            !access.pending());
        WidgetDrawing.button(context.graphics(), option(true), "pause", paused,
            !access.pending());
        if (focused)
            context.graphics().fill(r.x(), r.bottom() - 1, r.right(),
                r.bottom(), 0xfff5d477);
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public void setFocused(boolean value) {
        focused = value;
    }

    @Override
    public Click mouseClicked(double x, double y, int button) {
        if (button == 0) {
            if (option(false).contains(x, y))
                choose(false);
            else if (option(true).contains(x, y))
                choose(true);
        }
        return Click.CONSUME;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 263 || key == 262) {
            choose(key == 262);
            return true;
        }
        if (key == 32 || key == 257) {
            choose(!Boolean.TRUE.equals(access.value()));
            return true;
        }
        return false;
    }

    @Override
    public void actionResult(boolean accepted) {
        unconfirmed = !accepted;
    }
}
