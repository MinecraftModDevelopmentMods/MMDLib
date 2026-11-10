package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.RegistryAccess;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.layout.ElementFragmentLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Align;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.containers.BoxElement;
import zone.moddev.mc.lib.gui.model.containers.ContextElement;
import zone.moddev.mc.lib.gui.model.containers.SelectiveContainer;
import zone.moddev.mc.lib.gui.model.containers.ViewElement;
import zone.moddev.mc.lib.gui.sync.ElementStateView;
import zone.moddev.mc.lib.gui.sync.ElementStates;

class FragmentLayoutTest {
    private static Common named(String id) {
        var c = Common.empty();
        return new Common(
            Optional.of(id),
            c.value(),
            c.max(),
            c.tooltip(),
            c.tooltipShift(),
            c.clicks(),
            c.onHover(),
            c.hoverShows(),
            c.jeiRecipes(),
            0,
            0,
            0);
    }

    private record Leaf(int height) implements Element {
        public Common common() {
            return Common.empty();
        }

        public int[] measure(TextMeasure text) {
            return new int[]{30, height};
        }
    }

    @Test
    void stateControlsMeasurementAndAncestorVisibility() {
        var home = new Leaf(10);
        var details = new Leaf(30);
        var first = new ViewElement(named("home"), Optional.empty(), List.of(home));
        var second = new ViewElement(named("details"), Optional.empty(), List.of(details));
        var context = new ContextElement(named("pages"), List.of(first, second));
        var state = new ElementStates(List.of(context), RegistryAccess.EMPTY, () -> {
        });
        var initial = ElementFragmentLayout.of(context, 100, TextMeasure.NONE, state);
        assertEquals(10, initial.height());
        assertFalse(initial.visibility(state, e -> true).get(details));
        context.select(state, "details");
        var selected = ElementFragmentLayout.of(context, 100, TextMeasure.NONE, state);
        assertEquals(30 + TextMeasure.LINE_HEIGHT + 4, selected.height());
        assertEquals(TextMeasure.LINE_HEIGHT + 4, selected.bounds().get(details).y());
        assertFalse(selected.visibility(state, e -> true).get(home));
        assertTrue(selected.visibility(state, e -> true).get(details));
        assertFalse(selected.visibility(state, e -> e != context).get(details));
        assertFalse(selected.visibility(state, e -> e != second).get(details));
    }

    private static class ModSelection extends BoxElement implements SelectiveContainer {
        private final Element chosen;

        ModSelection(List<Element> children, Element chosen) {
            super(Common.empty(), false, 0, 0, Align.START, children);
            this.chosen = chosen;
        }

        public boolean childVisible(Element child, ElementStateView state) {
            return child == chosen;
        }
    }

    @Test
    void customSelectiveContainersComposeThroughNestedParents() {
        var visible = new Leaf(10);
        var hidden = new Leaf(20);
        var inner = new ModSelection(List.of(visible, hidden), visible);
        var outer = new ModSelection(List.of(inner), inner);
        var layout = ElementFragmentLayout.of(outer, 100, TextMeasure.NONE);
        var shown = layout.visibility(ElementStateView.DEFAULTS, e -> true);
        assertTrue(shown.get(visible));
        assertFalse(shown.get(hidden));
        assertFalse(layout.visibility(ElementStateView.DEFAULTS, e -> e != inner).get(visible));
        assertThrows(
            IllegalArgumentException.class,
            () -> ElementFragmentLayout.of(
                new ModSelection(List.of(inner, inner), inner), 100, TextMeasure.NONE));
    }
}
