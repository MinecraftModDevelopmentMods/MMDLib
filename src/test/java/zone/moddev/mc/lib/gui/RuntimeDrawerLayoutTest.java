package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import java.nio.charset.StandardCharsets;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.drawer.DrawerPlacement;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.layout.SideDrawerLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.containers.PlayerInventoryElement;

class RuntimeDrawerLayoutTest {
    @Test
    void explicitInventoryOffsetsMoveNativeSlotsToolbarAndControlsTogether()
        throws Exception {
        ScreenLayout previous = null;
        zone.moddev.mc.lib.gui.model.Screen previousScreen = null;
        for (boolean offset : new boolean[]{false, true}) {
            var xml = """
                <screen xmlns="https://moddev.zone/mmdlib/screen" title="test"
                    block-entity="net.minecraft.world.level.block.entity.BlockEntity">
                  <vbox offset-x="%s" offset-y="%s"><player-inventory id="inventory" offset-x="%s" offset-y="%s"/></vbox>
                  <inventory-controls id="controls"><button id="button" icon="mmdlib:icon_info" tooltip="test.tip"/></inventory-controls>
                </screen>
                """
                .formatted(offset ? 3 : 0, offset ? 4 : 0, offset ? 2 : 0,
                    offset ? 1 : 0);
            var screen = ScreenReader.read(
                ResourceLocation.parse("test:offset"), "offset.xml",
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            var layout = ScreenLayout.of(screen, TextMeasure.NONE, true);
            assertEquals(layout.bounds(screen.find("inventory").orElseThrow()),
                layout.region(PlayerInventoryElement.INVENTORY).orElseThrow());
            if (previous != null) {
                assertEquals(
                    previous.region(PlayerInventoryElement.INVENTORY)
                        .orElseThrow().offset(5, 5),
                    layout.region(PlayerInventoryElement.INVENTORY)
                        .orElseThrow());
                assertEquals(
                    previous.region(PlayerInventoryElement.TOOLBAR)
                        .orElseThrow().offset(5, 5),
                    layout.region(PlayerInventoryElement.TOOLBAR)
                        .orElseThrow());
                for (var id : new String[]{"controls", "button"})
                    assertEquals(
                        previous.bounds(previousScreen.find(id).orElseThrow())
                            .offset(5, 5),
                        layout.bounds(screen.find(id).orElseThrow()));
            }
            previous = layout;
            previousScreen = screen;
        }
    }

    @Test
    void tallInventoryControlsDoNotMoveEdgeTabsOrTheirContents()
        throws Exception {
        ScreenLayout previous = null;
        zone.moddev.mc.lib.gui.model.Screen previousScreen = null;
        for (boolean flat : new boolean[]{true, false}) {
            var xml = """
                <screen xmlns="https://moddev.zone/mmdlib/screen" title="test"
                    block-entity="net.minecraft.world.level.block.entity.BlockEntity">
                  <player-inventory/>
                  <label id="below" text="Below"/>
                  <inventory-controls><button flat="%s" icon="mmdlib:icon_info" tooltip="test.tip"/></inventory-controls>
                  <side-tab id="first" edge="right"><label id="first-child" text="test"/></side-tab>
                  <side-tab id="second" edge="right"><label id="second-child" text="test"/></side-tab>
                  <side-tab id="third" edge="right"><label id="third-child" text="test"/></side-tab>
                </screen>
                """
                .formatted(flat);
            var screen = ScreenReader.read(ResourceLocation.parse("test:tall"),
                "tall.xml",
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            var layout = ScreenLayout.of(screen, TextMeasure.NONE, true, true,
                Map.of());
            if (previous != null) {
                for (var id : new String[]{"first", "first-child", "second",
                    "second-child", "third", "third-child"})
                    assertEquals(
                        previous.bounds(previousScreen.find(id).orElseThrow()),
                        layout.bounds(screen.find(id).orElseThrow()), id);
                int dy = layout.region(PlayerInventoryElement.INVENTORY)
                    .orElseThrow().y()
                    - previous.region(PlayerInventoryElement.INVENTORY)
                        .orElseThrow().y();
                assertTrue(dy > 0);
                assertEquals(
                    previous.bounds(previousScreen.find("below").orElseThrow())
                        .y() + dy,
                    layout.bounds(screen.find("below").orElseThrow()).y());
            }
            previous = layout;
            previousScreen = screen;
        }
    }

    @Test
    void shortPanelsIncludeBothRuntimeTabsAndXmlTabs() throws Exception {
        var xml = """
            <screen xmlns="https://moddev.zone/mmdlib/screen" title="test" inventory="none"
                block-entity="net.minecraft.world.level.block.entity.BlockEntity">
              <label text="test"/>
              <side-tab edge="right"><label text="test"/></side-tab>
              <popout edge="right" kind="info" text="test"/>
            </screen>
            """;
        var screen = ScreenReader.read(ResourceLocation.parse("test:drawers"),
            "drawers.xml",
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        for (boolean automation : new boolean[]{false, true}) {
            var layout = ScreenLayout.of(screen, TextMeasure.NONE, automation,
                true, Map.of());
            int previous = SideDrawerLayout.MARGIN + (automation ? 2 : 1)
                * (DrawerPlacement.DEFAULT_HEIGHT + DrawerPlacement.DEFAULT_GAP)
                - DrawerPlacement.DEFAULT_GAP;
            for (var tab : screen.tabs()) {
                var bounds = layout.bounds(tab);
                assertTrue(
                    bounds.y() >= previous + DrawerPlacement.DEFAULT_GAP);
                assertTrue(bounds.bottom() <= layout.panel().bottom()
                    - SideDrawerLayout.MARGIN);
                previous = bounds.bottom();
            }
        }
    }
}
