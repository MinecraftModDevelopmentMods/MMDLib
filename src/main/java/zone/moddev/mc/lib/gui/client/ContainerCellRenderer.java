package zone.moddev.mc.lib.gui.client;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.containers.ContainerInventoryElement;
import zone.moddev.mc.lib.gui.model.elements.ContainerSlotElement;

/** Registered drawing for virtual container cells; independent of drawer input and transport. */
final class ContainerCellRenderer {
    private static final SlotRenderer SLOTS = new SlotRenderer();

    private ContainerCellRenderer() {
    }

    static void inventory(ContainerInventoryElement element, ElementRenderers.Context c) {
        var headings = element.headings();
        for (int i = 0; i < headings.size(); i++)
            label(c, headings.get(i).getString(), element.headingBounds(i));
        for (var slot : element.slots())
            label(c, element.cellLabel(slot).getString(), element.labelBounds(slot));
    }

    private static void label(ElementRenderers.Context c, String text, Rect relative) {
        var font = c.access().font();
        c.graphics()
            .drawString(
                font,
                font.plainSubstrByWidth(text, relative.width()),
                c.bounds().x() + relative.x(),
                c.bounds().y() + relative.y(),
                0xff404040,
                false);
    }

    static void slot(ContainerSlotElement element, ElementRenderers.Context c) {
        var g = c.graphics();
        var b = c.bounds();
        if (!element.available()) {
            g.drawString(
                c.access().font(), element.placeholder(), b.x() + 6, b.y() + 5, 0xff666666, false);
            return;
        }
        var contents = c.access().contents(element);
        SLOTS.draw(g, b, element, contents, c.access().hint(element));
        int[] xy = element.itemPosition(b);
        var original = new Rect(xy[0], xy[1], 16, 16);
        var item = element.nativeBounds(original);
        if (element.renderNative() && !contents.isEmpty()) {
            g.pose().pushPose();
            try {
                g.pose().translate(item.x(), item.y(), 0);
                g.pose().scale(item.width() / 16f, item.height() / 16f, 1);
                g.renderItem(contents, 0, 0);
                g.renderItemDecorations(c.access().font(), contents, 0, 0);
            } finally {
                g.pose().popPose();
            }
        }
        if (element.cellHitBounds(b).contains(c.mouseX(), c.mouseY())
            || c.value() instanceof ContainerSlotElement.Display display && display.selected()) {
            var hit = element.highlightBounds(original);
            g.fill(hit.x(), hit.y(), hit.right(), hit.bottom(), 0x80ffffff);
        }
    }
}
