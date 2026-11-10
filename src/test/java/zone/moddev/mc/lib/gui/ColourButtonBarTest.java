package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;

import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.gui.client.ColourButtonBar;
import zone.moddev.mc.lib.gui.layout.GroupOrder;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.ElementGroup;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.containers.PlayerInventoryElement;

class ColourButtonBarTest {
    private Screen screen() throws Exception {
        String xml = """
            <screen xmlns="https://moddev.zone/mmdlib/screen" title="test.title"
              block-entity="zone.moddev.mc.lib.gui.ScreenLoaderTest$Press">
              <hbox><slot id="first" index="0"/><slot id="second" index="1"/><slot id="third" index="2"/></hbox>
              <inventory-controls><button flat="true" id="pause" icon="mmdlib:icon_pause" tooltip="test.pause" on-click="test:pause"/></inventory-controls>
            </screen>
            """;
        return ScreenReader.read(ResourceLocation.parse("test:bar"), "bar.xml",
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private ElementGroup group(String name, int member, String... anchors) {
        return new ElementGroup(name, List.of(anchors),
            Optional.of(new ElementGroup.Members(ResourceType.ITEMS.id(),
                "items", List.of(member))));
    }

    private ColourButtonBar<String> bar(List<String> order,
        java.util.function.Consumer<String> select) {
        return new ColourButtonBar<>(order.stream()
            .map(id -> new ColourButtonBar.Entry<>(id, 0x123456)).toList(),
            select);
    }

    @Test
    void followsScreenOrderWithoutChangingPersistentGroupIndices()
        throws Exception {
        var groups = List.of(group("third", 2, "third"),
            group("first", 0, "first", "second"), group("second", 1),
            group("hidden", 7, "absent"));
        var screen = screen();
        var order = GroupOrder.of(screen,
            groups.stream().map(ElementGroup::id).toList(),
            element -> element.associatedGroups(screen, groups));
        assertEquals(List.of("first", "second", "third", "hidden"), order);
        var clicked = new AtomicReference<String>();
        var bar = bar(order, clicked::set);
        bar.layout(new Rect(10, 20, 100, 14), "third");
        assertTrue(bar.click(10, 21)); // No blank arrow gutter when every
                                       // colour fits.
        assertEquals("first", clicked.get());
        assertTrue(bar.click(29, 21));
        assertEquals("second", clicked.get());
        clicked.set(null);
        bar.click(25, 21); // Four-pixel button gap is not another inventory.
        assertNull(clicked.get());
        assertFalse(bar.click(111, 21));
    }

    @Test
    void overflowScrollsAndResizeKeepsSelectionReachable() throws Exception {
        var clicked = new AtomicReference<String>();
        var bar = bar(List.of("first", "second", "third", "hidden"),
            clicked::set);
        bar.layout(new Rect(0, 0, 34, 14), "first"); // One colour, two
                                                     // navigation arrows.
        bar.click(33, 5);
        bar.click(15, 5);
        assertEquals("second", clicked.get());
        bar.scroll(15, 5, -100);
        bar.click(15, 5);
        assertEquals("hidden", clicked.get());
        bar.layout(new Rect(0, 0, 100, 14), new String("hidden"));
        bar.click(1, 5);
        assertEquals("first", clicked.get());
        bar.layout(new Rect(0, 0, 34, 14), new String("hidden"));
        bar.click(15, 5);
        assertEquals("hidden", clicked.get());
    }

    @Test
    void emptyAndHiddenBarsDoNotConsumeInputAndDuplicateIdsAreRejected() {
        var empty = bar(List.of(), id -> fail("Empty bar selected an ID"));
        empty.layout(new Rect(0, 0, 100, 14), null);
        assertFalse(empty.click(1, 1));
        assertFalse(empty.scroll(1, 1, -1));
        var hidden = bar(List.of("one"),
            id -> fail("Hidden bar selected an ID"));
        hidden.layout(null, "one");
        assertFalse(hidden.click(1, 1));
        assertThrows(IllegalArgumentException.class,
            () -> bar(List.of("same", new String("same")), id -> {
            }));
    }

    @Test
    void sharesExactlyOneRowAndReservesRightHandControls() throws Exception {
        var screen = screen();
        var with = ScreenLayout.of(screen, TextMeasure.NONE, true);
        var without = ScreenLayout.of(screen, TextMeasure.NONE, false);
        assertEquals(without.panel(), with.panel());
        var row = with.region(PlayerInventoryElement.TOOLBAR).orElseThrow();
        var inventory = with.region(PlayerInventoryElement.INVENTORY)
            .orElseThrow();
        var pause = with.bounds(screen.find("pause").orElseThrow());
        assertEquals(inventory.x(), row.x());
        assertEquals(PlayerInventoryElement.slot(inventory, 17).right(),
            pause.right());
        assertEquals(row.y(), pause.y());
        assertEquals(pause.x(), row.right() + ScreenLayout.GAP);
        assertEquals(row.bottom() + ScreenLayout.GAP,
            PlayerInventoryElement.slot(inventory, 9).y());
    }
}
