package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.containers.PlayerInventoryElement;

class PlayerInventoryLayoutTest {
    @Test
    void automaticAndExplicitInventoriesKeepAllSlotsInsideWithoutOverlap() throws Exception {
        for (String body : new String[]{
            "",
            "<player-inventory/>",
            "<vbox padding=\"7\"><player-inventory offset-x=\"2\" offset-y=\"3\"/></vbox>"
        }) {
            for (boolean automation : new boolean[]{false, true}) {
                var layout = layout("", body, automation);
                var inventory = layout.region(PlayerInventoryElement.INVENTORY).orElseThrow();
                var slots = new ArrayList<Rect>();
                for (int index = 0; index <= 40; index++) {
                    var slot = PlayerInventoryElement.slot(inventory, index);
                    assertTrue(slot.x() >= inventory.x() && slot.y() >= inventory.y());
                    assertTrue(
                        slot.right() <= inventory.right() && slot.bottom() <= inventory.bottom());
                    for (var other : slots)
                        assertFalse(
                            slot.x() < other.right()
                                && slot.right() > other.x()
                                && slot.y() < other.bottom()
                                && slot.bottom() > other.y());
                    slots.add(slot);
                }
                assertEquals(slots.get(0).y(), slots.get(40).y(), "Offhand aligns with hotbar");
                assertTrue(slots.get(39).y() < slots.get(38).y());
                assertTrue(slots.get(38).y() < slots.get(37).y());
                assertTrue(slots.get(37).y() < slots.get(36).y());
                if (automation) {
                    var toolbar = layout.region(PlayerInventoryElement.TOOLBAR).orElseThrow();
                    assertEquals(slots.get(39).y(), toolbar.y());
                    assertEquals(slots.get(9).y(), toolbar.bottom() + ScreenLayout.GAP);
                    assertEquals(slots.get(17).right(), toolbar.right());
                }
            }
        }
    }

    @Test
    void inventoryNoneOmitsEquipmentToo() throws Exception {
        assertTrue(
            layout(" inventory=\"none\"", "", false)
                .region(PlayerInventoryElement.INVENTORY)
                .isEmpty());
    }

    private static ScreenLayout layout(String attributes, String body, boolean automation)
        throws Exception {
        String xml = "<screen xmlns=\"https://moddev.zone/mmdlib/screen\" title=\"test\" "
            + "block-entity=\"net.minecraft.world.level.block.entity.BlockEntity\""
            + attributes
            + "><label text=\"test.body\"/>"
            + body
            + "</screen>";
        var screen = ScreenReader.read(
            ResourceLocation.parse("test:equipment"),
            "equipment.xml",
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return ScreenLayout.of(screen, TextMeasure.NONE, automation);
    }
}
