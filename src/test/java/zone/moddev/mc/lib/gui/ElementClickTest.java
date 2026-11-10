package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.client.ElementBehavior;
import zone.moddev.mc.lib.gui.client.ElementInput;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;

class ElementClickTest {
    private record Leaf(Common common) implements Element {
    }

    private static class Fixture {
        final Leaf bottom = new Leaf(Common.empty()),
            top = new Leaf(Common.empty());
        final List<String> events = new ArrayList<>();
        final Set<Element> hidden = Collections
            .newSetFromMap(new IdentityHashMap<>());
        ElementBehavior.Click topResult = ElementBehavior.Click.PASS;
        final ElementInput<Leaf, ElementBehavior> input = new ElementInput<>(
            List.of(bottom, top), e -> new ElementBehavior() {
                public boolean focusable() {
                    return true;
                }

                public Click mouseClicked(double x, double y, int button) {
                    events.add(e == top ? "top" : "bottom");
                    return e == top ? topResult : Click.CONSUME;
                }

                public void setFocused(boolean focused) {
                    events.add((e == top ? "top:" : "bottom:") + focused);
                }

                public boolean mouseReleased(double x, double y, int button) {
                    events.add("release");
                    return true;
                }

                public boolean mouseScrolled(double x, double y, double dx,
                    double dy) {
                    events.add("scroll");
                    return true;
                }
            }, e -> !hidden.contains(e), e -> new Rect(0, 0, 40, 40),
            (x, y) -> new Rect(0, 0, 20, 20).contains(x, y), (e, entered) -> {
            });

        Fixture() {
            input.update();
        }
    }

    @Test
    void lastVisibleHitWinsAndPassFallsThroughByIdentity() {
        var f = new Fixture();
        assertEquals(f.bottom, f.top);
        assertTrue(f.input.click(5, 5, 0));
        assertEquals(List.of("top", "bottom", "bottom:true"), f.events);
        assertSame(f.bottom, f.input.focused());
        f.topResult = ElementBehavior.Click.CONSUME;
        f.events.clear();
        assertTrue(f.input.click(5, 5, 0));
        assertEquals(List.of("bottom:false", "top", "top:true"), f.events);
        f.hidden.add(f.top);
        f.input.update();
        f.events.clear();
        assertTrue(f.input.click(5, 5, 0));
        assertEquals(List.of("bottom", "bottom:true"), f.events);
    }

    @Test
    void clippedOrMissedPressClearsFocusAndAllowsNativeFallback() {
        var f = new Fixture();
        assertTrue(f.input.click(5, 5, 0));
        f.events.clear();
        assertFalse(f.input.click(30, 5, 0));
        assertNull(f.input.focused());
        assertEquals(List.of("bottom:false"), f.events);
        assertFalse(f.input.scroll(30, 5, 0, 1));
        assertFalse(f.input.click(50, 50, 0));
        f.input.removed();
        assertFalse(f.input.click(5, 5, 0));
    }

    @Test
    void captureConsumesOutsidePressesAndKeepsReleaseOwner() {
        var f = new Fixture();
        f.topResult = ElementBehavior.Click.CAPTURE;
        assertTrue(f.input.click(5, 5, 0));
        f.events.clear();
        assertTrue(f.input.click(50, 50, 1));
        assertTrue(f.events.isEmpty());
        assertTrue(f.input.capturing());
        assertTrue(f.input.release(50, 50, 0));
        assertEquals(List.of("release"), f.events);
        assertFalse(f.input.capturing());
    }

    @Test
    void aFreshPressClearsCancelledGestureSuppression() {
        var f = new Fixture();
        f.topResult = ElementBehavior.Click.CAPTURE;
        f.input.click(5, 5, 0);
        f.input.cancelInteraction();
        assertFalse(f.input.click(50, 50, 0));
        assertFalse(f.input.release(50, 50, 0));
    }
}
