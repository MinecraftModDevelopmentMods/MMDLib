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
