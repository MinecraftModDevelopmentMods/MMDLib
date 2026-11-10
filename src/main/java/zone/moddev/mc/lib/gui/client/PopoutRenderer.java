package zone.moddev.mc.lib.gui.client;

import java.util.List;

import net.minecraft.util.FormattedCharSequence;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.elements.PopoutElement;

/** Registered renderer adapter for the client-owned {@link PopoutDrawer}. */
public final class PopoutRenderer {
    private PopoutRenderer() {
    }

    public record Presentation(Rect body, List<FormattedCharSequence> lines, int scroll) {
        public Presentation {
            lines = List.copyOf(lines);
        }
    }

    public static void tab(PopoutElement element, ElementRenderers.Context c) {
        PopoutDrawer.drawTab(element, c);
    }

    public static void body(PopoutElement element, ElementRenderers.Context c) {
        c.access()
            .drawerPresentation(element, Presentation.class)
            .ifPresent(p -> PopoutDrawer.drawBody(element, c, p));
    }
}
