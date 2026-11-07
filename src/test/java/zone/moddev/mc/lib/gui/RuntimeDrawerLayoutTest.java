package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.load.ScreenReader;

class RuntimeDrawerLayoutTest {
  @Test
  void tallInventoryControlsDoNotMoveEdgeTabsOrTheirContents() throws Exception {
    ScreenLayout previous = null;
    zone.moddev.mc.lib.gui.model.Screen previousScreen = null;
    for (boolean flat : new boolean[] {true, false}) {
      var xml =
          """
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
      var screen =
          ScreenReader.read(
              ResourceLocation.parse("test:tall"),
              "tall.xml",
              new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      var layout = ScreenLayout.of(screen, TextMeasure.NONE, true, true, Map.of());
      if (previous != null) {
        for (var id :
            new String[] {"first", "first-child", "second", "second-child", "third", "third-child"})
          assertEquals(
              previous.bounds(previousScreen.find(id).orElseThrow()),
              layout.bounds(screen.find(id).orElseThrow()),
              id);
        int dy = layout.inventory().orElseThrow().y() - previous.inventory().orElseThrow().y();
        assertTrue(dy > 0);
        assertEquals(
            previous.bounds(previousScreen.find("below").orElseThrow()).y() + dy,
            layout.bounds(screen.find("below").orElseThrow()).y());
      }
      previous = layout;
      previousScreen = screen;
    }
  }

  @Test
  void shortPanelsIncludeBothRuntimeTabsAndXmlTabs() throws Exception {
    var xml =
        """
        <screen xmlns="https://moddev.zone/mmdlib/screen" title="test" inventory="none"
            block-entity="net.minecraft.world.level.block.entity.BlockEntity">
          <label text="test"/>
          <side-tab edge="right"><label text="test"/></side-tab>
          <popout edge="right" kind="info" text="test"/>
        </screen>
        """;
    var screen =
        ScreenReader.read(
            ResourceLocation.parse("test:drawers"),
            "drawers.xml",
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    for (boolean automation : new boolean[] {false, true}) {
      var layout = ScreenLayout.of(screen, TextMeasure.NONE, automation, true, Map.of());
      int previous =
          ScreenLayout.TAB_PADDING
              + (automation ? 2 : 1) * (ScreenLayout.POPOUT_HEIGHT + ScreenLayout.TAB_GAP)
              - ScreenLayout.TAB_GAP;
      for (var tab : screen.tabs()) {
        var bounds = layout.bounds(tab);
        assertTrue(bounds.y() >= previous + ScreenLayout.TAB_GAP);
        assertTrue(bounds.bottom() <= layout.panel().bottom() - ScreenLayout.TAB_PADDING);
        previous = bounds.bottom();
      }
    }
  }
}
