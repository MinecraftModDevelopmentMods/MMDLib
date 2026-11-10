package zone.moddev.mc.lib.gui.client;

import net.minecraft.client.gui.GuiGraphics;

import zone.moddev.mc.lib.gui.layout.Rect;

/** Optional element-owned highlight drawing exposed through its registered client behavior. */
@FunctionalInterface
public interface HighlightPresentation {
    HighlightPresentation OUTLINE = (graphics, bounds, request) -> request.render(graphics, bounds);

    void highlight(GuiGraphics graphics, Rect bounds, ElementHighlight request);
}
