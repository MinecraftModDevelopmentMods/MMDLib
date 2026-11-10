package zone.moddev.mc.lib.gui.client;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.api.automation.FilterControl;

final class FilterSamplesBehavior implements ElementBehavior {
    private static final SlotRenderer RENDERER = new SlotRenderer();
    private final FilterControl.Samples element;
    private final ElementAccess access;

    FilterSamplesBehavior(FilterControl.Samples element, ElementAccess access) {
        this.element = element;
        this.access = access;
    }

    private int index(double x, double y) {
        return element.sampleIndex(access.bounds(), x, y);
    }

    public Click mouseClicked(double x, double y, int button) {
        int index = index(x, y);
        if (index < 0 || button < 0 || button > 1)
            return Click.PASS;
        FilterEdits.submit(access, element, index, button == 1 ? "clear" : "sample");
        return Click.CONSUME;
    }

    public List<Component> tooltip(int x, int y) {
        int index = index(x, y);
        if (index < 0)
            return List.of();
        return element.sampleTooltip(FilterDrawing.configuration(access.value()), index);
    }

    static void render(FilterControl.Samples element, ElementRenderers.Context c) {
        var r = c.bounds();
        for (int i = 0; i < element.count(); i++)
            RENDERER.draw(
                c.graphics(),
                element.sampleBounds(r, i),
                element.sampleSlot(i),
                element.sampleDisplay(FilterDrawing.configuration(c.value()), i),
                ItemStack.EMPTY);
    }
}
