package zone.moddev.mc.lib.gui;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.gui.client.InventoryColorButtonBar;
import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.Screen;

class InventoryColorButtonBarTest {
  private Screen screen() throws Exception {
    String xml =
        """
        <screen xmlns="https://moddev.zone/mmdlib/screen" title="test.title"
          block-entity="zone.moddev.mc.lib.gui.ScreenLoaderTest$Press">
          <hbox><slot id="first" index="0"/><slot id="second" index="1"/><slot id="third" index="2"/></hbox>
          <inventory-controls><button flat="true" id="pause" icon="mmdlib:icon_pause" tooltip="test.pause" on-click="test:pause"/></inventory-controls>
        </screen>
        """;
    return ScreenReader.read(
        ResourceLocation.parse("test:bar"),
        "bar.xml",
        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  private ResourceGroup<?> group(String name, int member, String... anchors) {
    return new ResourceGroup<>(
        name,
        Component.literal(name),
        0x123456,
        ResourceType.ITEMS,
        IoMode.INPUT,
        List.of(member),
        List.of(anchors),
        new SideConfig(IoMode.INPUT));
  }

  @Test
  void followsScreenOrderWithoutChangingPersistentGroupIndices() throws Exception {
    var groups =
        List.of(
            group("third", 2, "third"),
            group("first", 0, "first", "second"),
            group("second", 1),
            group("hidden", 7, "absent"));
    assertEquals(List.of(1, 2, 0, 3), InventoryColorOrder.of(screen(), groups));
    var clicked = new AtomicInteger(-1);
    var bar = new InventoryColorButtonBar(screen(), groups, clicked::set);
    bar.layout(new Rect(10, 20, 100, 14), 0);
    assertTrue(bar.click(10, 21)); // No blank arrow gutter when every colour fits.
    assertEquals(1, clicked.get());
    assertTrue(bar.click(29, 21));
    assertEquals(2, clicked.get());
    clicked.set(-1);
    bar.click(25, 21); // Four-pixel button gap is not another inventory.
    assertEquals(-1, clicked.get());
    assertFalse(bar.click(111, 21));
  }

  @Test
  void overflowScrollsAndResizeKeepsSelectionReachable() throws Exception {
    var groups =
        List.of(group("first", 0), group("second", 1), group("third", 2), group("hidden", 7));
    var clicked = new AtomicInteger(-1);
    var bar = new InventoryColorButtonBar(screen(), groups, clicked::set);
    bar.layout(new Rect(0, 0, 34, 14), 0); // One colour, two navigation arrows.
    bar.click(33, 5);
    bar.click(15, 5);
    assertEquals(1, clicked.get());
    bar.scroll(15, 5, -100);
    bar.click(15, 5);
    assertEquals(3, clicked.get());
    bar.layout(new Rect(0, 0, 100, 14), 3);
    bar.click(1, 5);
    assertEquals(0, clicked.get());
    bar.layout(new Rect(0, 0, 34, 14), 3);
    bar.click(15, 5);
    assertEquals(3, clicked.get());
  }

  @Test
  void sharesExactlyOneRowAndReservesRightHandControls() throws Exception {
    var screen = screen();
    var with = ScreenLayout.of(screen, TextMeasure.NONE, true);
    var without = ScreenLayout.of(screen, TextMeasure.NONE, false);
    assertEquals(without.panel(), with.panel());
    var row = with.inventoryColors().orElseThrow();
    var inventory = with.inventory().orElseThrow();
    var pause = with.bounds(screen.find("pause").orElseThrow());
    assertEquals(inventory.x(), row.x());
    assertEquals(inventory.right(), pause.right());
    assertEquals(row.y(), pause.y());
    assertEquals(pause.x(), row.right() + ScreenLayout.GAP);
    assertEquals(row.bottom() + ScreenLayout.GAP, inventory.y());
  }
}
