package zone.moddev.mc.lib.gui.client;

import net.minecraft.client.gui.GuiGraphics;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.containers.ColouredGroupElement;

/**
 * The wrapper owns its existing outline and how external controls emphasize it.
 */
public class ColouredGroupBehavior extends DefaultElementBehavior
    implements HighlightPresentation, GroupColourProvider {
    private final ColouredGroupElement group;

    public ColouredGroupBehavior(ColouredGroupElement group,
        ElementAccess access) {
        super(access);
        this.group = group;
    }

    public int groupColour() {
        return group.colour();
    }

    public void highlight(GuiGraphics graphics, Rect bounds,
        ElementHighlight request) {
        // Backgrounds identify the group; outlines are reserved for emphasis.
        if (request.kind() == ElementHighlight.Kind.DECORATION)
            return;
        if (request.kind() == ElementHighlight.Kind.EMPHASIS)
            ColouredGroupRenderer.draw(group, graphics, bounds, true);
        else
            request.render(graphics, bounds);
    }
}
