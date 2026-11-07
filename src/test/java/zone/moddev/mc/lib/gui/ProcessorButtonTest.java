package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.ProcessorButtonState;
import zone.moddev.mc.lib.gui.model.Screen;

class ProcessorButtonTest {
  @Test
  void omittedKeyStillCreatesAProcessorControl() throws Exception {
    var button = (Element.Button) read("<processor-button/>").body().getFirst();
    assertTrue(button.processorControl());
    assertTrue(button.processorKey().isEmpty());
    var ordinary =
        (Element.Button)
            read("<button icon=\"mmdlib:icon_info\" tooltip=\"test.tip\"/>").body().getFirst();
    assertFalse(ordinary.processorControl());
  }

  private Screen read(String body) throws Exception {
    var xml =
        "<screen xmlns=\"https://moddev.zone/mmdlib/screen\" title=\"test.screen\""
            + " block-entity=\"zone.moddev.mc.lib.gui.ScreenLoaderTest$Press\">"
            + body
            + "</screen>";
    return ScreenReader.read(
        ResourceLocation.parse("test:screen"),
        "processor.xml",
        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void showsCurrentPauseProcessingAndIdleStatesWithoutMachineSpecificBindings() throws Exception {
    var button =
        (Element.Button) read("<processor-button processor-key=\"lane_1\"/>").body().getFirst();
    assertEquals("lane_1", button.processorKey().orElseThrow());
    assertTrue(button.flat());
    assertTrue(button.common().value().isEmpty());
    assertFalse(button.common().clicks().any());
    assertEquals(
        ResourceLocation.parse("mmdlib:icon_processor_pause"),
        button.iconFor(ProcessorButtonState.of(true, false).wireValue()).orElseThrow());
    assertEquals(
        ResourceLocation.parse("mmdlib:icon_processor_stop"),
        button.iconFor(ProcessorButtonState.of(false, true).wireValue()).orElseThrow());
    assertEquals(
        ResourceLocation.parse("mmdlib:icon_processor_play"),
        button.iconFor(ProcessorButtonState.of(false, false).wireValue()).orElseThrow());
    assertEquals(ProcessorButtonState.PAUSED, ProcessorButtonState.of(true, true));
    assertTrue(button.common().tooltip().isPresent());
  }

  @Test
  void stateDecodingNeverTreatsAnUnknownSnapshotAsAManualPause() {
    assertTrue(ProcessorButtonState.fromWire(null).isEmpty());
    assertTrue(ProcessorButtonState.fromWire(java.math.BigInteger.valueOf(3)).isEmpty());
    for (var state : ProcessorButtonState.values())
      assertEquals(state, ProcessorButtonState.fromWire(state.wireValue()).orElseThrow());
  }

  @Test
  void worksAlongsideOrdinaryButtonsInInventoryControls() throws Exception {
    var screen =
        read(
            """
            <slot index="0"/>
            <inventory-controls>
              <processor-button processor-key="a"/>
              <button icon="mmdlib:icon_info" tooltip="test.info"/>
              <processor-button processor-key="b" flat="false" text="test.b"/>
            </inventory-controls>
            """);
    assertEquals(3, screen.body().get(1).children().size());
    assertFalse(((Element.Button) screen.body().get(1).children().getLast()).flat());
  }

  @Test
  void rejectsBlankKeysAndConflictingActionsOrBindings() {
    for (var attributes :
        new String[] {
          "processor-key=\"\"",
          "processor-key=\"   \"",
          "value=\"#getEnergy\"",
          "on-click=\"test:toggle\"",
          "processor-key=\"x\" value=\"#getEnergy\"",
          "processor-key=\"x\" max=\"#getEnergy\"",
          "processor-key=\"x\" on-click=\"test:toggle\"",
          "processor-key=\"x\" jei-recipes=\"test:recipe\""
        })
      assertThrows(
          ScreenException.class, () -> read("<processor-button " + attributes + "/>"), attributes);
  }

  @Test
  void requiresHostSupportAtBinding() throws Exception {
    var screen = read("<processor-button/>");
    var error =
        assertThrows(
            ScreenException.class, () -> ScreenBinder.bind(screen, getClass().getClassLoader()));
    assertTrue(error.getMessage().contains("requires a ScreenHost"));
  }
}
