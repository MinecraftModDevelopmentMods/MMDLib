package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.client.ElementRenderers;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.ElementSnapshot;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.model.SlotSize;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.containers.InventoryElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.gui.runtime.ScreenClick;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;

/** Implementations deliberately live outside the library element package. */
class ModElementTest {
    private record ModElement(Common common) implements Element {
        public int[] measure(TextMeasure text) {
            return new int[]{23, 17};
        }

        public boolean interactive() {
            return true;
        }

        public boolean interact(ScreenClick click) {
            return true;
        }

        public Optional<ElementSnapshot> snapshot(ScreenHost host) {
            return Optional.of(new ElementSnapshot(7, 10));
        }
    }

    private record ModContainer(Common common, List<Element> children) implements Element {
        public int[] measure(TextMeasure text) {
            return new int[]{40, 30};
        }

        public Map<Element, Rect> childLayout(Rect bounds, TextMeasure text) {
            return Map.of(children.getFirst(), new Rect(4, 5, 23, 17));
        }
    }

    private record ModSlot(Common common, int index, Appearance appearance) implements SlotElement {
        public SlotKind kind() {
            return SlotKind.NORMAL;
        }
    }

    private Screen screen(Element element) throws Exception {
        return new Screen(
            ResourceLocation.parse("test:extension"),
            "extension.java",
            new Text("test", Optional.empty()),
            ElementInteractionTest.Host.class.getName(),
            false,
            List.of(element),
            List.of());
    }

    @Test
    void externalElementUsesLayoutInteractionSnapshotsAndTypedRendering() throws Exception {
        var element = new ModElement(Common.empty());
        var container = new ModContainer(Common.empty(), List.of(element));
        var layout = ScreenLayout.of(screen(container), TextMeasure.NONE);
        assertEquals(40, layout.bounds(container).width());
        assertEquals(layout.bounds(container).x() + 4, layout.bounds(element).x());
        assertEquals(layout.bounds(container).y() + 5, layout.bounds(element).y());
        assertTrue(element.interactive());
        assertTrue(element.interact(null));
        assertEquals(7, element.snapshot(null).orElseThrow().value());
        var renderers = new ElementRenderers();
        var rendered = new java.util.ArrayList<Element>();
        renderers.register(ModElement.class, (value, context) -> rendered.add(value));
        renderers
            .require(ModElement.class)
            .accept(
                new ElementRenderers.Context(null, element, layout.bounds(element), 7, 10, 0, 0,
                    0));
        assertEquals(List.of(element), rendered);
        assertThrows(
            IllegalArgumentException.class,
            () -> renderers.register(ModElement.class, (value, context) -> {
            }));
        assertThrows(IllegalArgumentException.class, () -> renderers.require(ModContainer.class));
    }

    @Test
    void externalSlotUsesInventoryGeometry() throws Exception {
        var slot = new ModSlot(
            Common.empty(),
            0,
            SlotElement.Appearance.standard(SlotSize.NORMAL, Optional.empty(), Optional.empty()));
        var inventory = new InventoryElement(Common.empty(), "custom", 1, 0, List.of(slot));
        var layout = ScreenLayout.of(screen(inventory), TextMeasure.NONE);
        assertEquals(18, layout.bounds(slot).width());
        assertEquals(18, layout.bounds(inventory).height());
        assertFalse(slot.ghost());
    }
}
