package zone.moddev.mc.mmdlib.util;

import java.awt.Color;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import zone.moddev.mc.mmdlib.container.IWidgetContainer;
import zone.moddev.mc.mmdlib.container.IWidgetContainerProxy;
import mcp.MethodsReturnNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class GuiUtils {
    private GuiUtils() {}

    @Nullable
    public static IWidgetContainer getWidgetContainer(Object thing) {
        if (thing instanceof IWidgetContainer) {
            return IWidgetContainer.class.cast(thing);
        }

        if (thing instanceof IWidgetContainerProxy) {
            return IWidgetContainerProxy.class.cast(thing).getWidgetContainer();
        }

        return null;
    }

    public static int applyAlpha(int color, int alpha) {
        Color base = new Color(color);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha).getRGB();
    }
}
