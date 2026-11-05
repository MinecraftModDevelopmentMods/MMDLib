package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.gui.layout.FilterPanelLayout;

class FilterPanelLayoutTest {
  private FilterControl toggle(String id) {
    return new FilterControl.Toggle(
        id, ResourceLocation.parse("test:toggle"), Component.literal(id));
  }

  @Test
  void sampleCountsWrapToAvailableWidth() {
    var root =
        new FilterControl.Box(
            "panel",
            false,
            List.of(toggle("deny"), new FilterControl.Samples("samples", 36, ResourceType.ITEMS)));
    var layout = FilterPanelLayout.of(root, 100);
    assertEquals(16, layout.controls().get("samples").y());
    assertEquals(144, layout.controls().get("samples").height());
    assertEquals(160, layout.height());
  }

  @Test
  void horizontalControlsWrapWithoutOverlap() {
    var layout =
        FilterPanelLayout.of(
            new FilterControl.Box("row", true, List.of(toggle("a"), toggle("b"), toggle("c"))), 18);
    assertEquals(0, layout.controls().get("a").y());
    assertEquals(16, layout.controls().get("b").y());
    assertEquals(32, layout.controls().get("c").y());
    assertEquals(46, layout.height());
  }

  @Test
  void duplicateIdsAndExcessiveSamplesAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            FilterPanelLayout.of(
                new FilterControl.Box("root", false, List.of(toggle("a"), toggle("a"))), 100));
    assertThrows(
        IllegalArgumentException.class,
        () -> new FilterControl.Samples("samples", 257, ResourceType.ITEMS));
  }
}
