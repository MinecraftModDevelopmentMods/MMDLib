package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.load.*;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.model.containers.ColouredGroupElement;
import zone.moddev.mc.lib.gui.model.elements.*;

class SpriteElementsTest {
  private static final ResourceLocation A = ResourceLocation.parse("test:a"),
      B = ResourceLocation.parse("test:b");

  private Screen read(String body) throws Exception {
    String xml =
        "<screen xmlns=\"https://moddev.zone/mmdlib/screen\" title=\"test.screen\""
            + " inventory=\"none\""
            + " block-entity=\"zone.moddev.mc.lib.gui.ScreenLoaderTest$Press\">"
            + body
            + "</screen>";
    return ScreenReader.read(
        ResourceLocation.parse("test:sprites"),
        "sprites.xml",
        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void animationRespectsFrameBoundariesLoopsAndStops() {
    var animation =
        new AnimatedSpriteElement(Common.empty(), List.of(A, B), 17, 11, 0xffffffff, 4, true);
    assertEquals(A, animation.frameAt(-1));
    assertEquals(A, animation.frameAt(3.99));
    assertEquals(B, animation.frameAt(4));
    assertEquals(A, animation.frameAt(8));
    var once =
        new AnimatedSpriteElement(Common.empty(), List.of(A, B), 17, 11, 0xffffffff, 4, false);
    assertEquals(B, once.frameAt(100));
    assertArrayEquals(new int[] {17, 11}, animation.measure(TextMeasure.NONE));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AnimatedSpriteElement(Common.empty(), List.of(), 1, 1, -1, 1, true));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AnimatedSpriteElement(Common.empty(), List.of(A), 1, 1, -1, 0, true));
  }

  @Test
  void progressClipsInEachDirectionAndClampsValues() {
    for (var direction : ProgressDirection.values()) {
      var progress = new ProgressSpriteElement(Common.empty(), A, B, 24, 16, direction);
      Rect half =
          switch (direction) {
            case RIGHT -> new Rect(0, 0, 12, 16);
            case LEFT -> new Rect(12, 0, 12, 16);
            case UP -> new Rect(0, 8, 24, 8);
            case DOWN -> new Rect(0, 0, 24, 8);
          };
      assertEquals(half, progress.fill(24, 16, .5));
      assertEquals(new Rect(0, 0, 24, 16), progress.fill(24, 16, 2));
      assertEquals(progress.fill(24, 16, 0), progress.fill(24, 16, -1));
      assertEquals(progress.fill(24, 16, 0), progress.fill(24, 16, Double.NaN));
      var layers = progress.layers(new Rect(100, 200, 24, 16), 0, .5);
      assertEquals(A, layers.getFirst().sprite());
      assertEquals(B, layers.getLast().sprite());
      assertEquals(half, layers.getLast().clip());
      assertEquals(0, layers.getLast().rotation());
    }
  }

  @Test
  void legacyArrowUsesProgressSpritesAndOnlyRotatesDefaultArtwork() {
    var arrow = new ProgressElement(Common.empty(), Optional.empty(), ProgressDirection.UP);
    assertArrayEquals(new int[] {16, 24}, arrow.measure(TextMeasure.NONE));
    var layer = arrow.spriteElement().layers(new Rect(0, 0, 16, 24), 0, .5).getLast();
    assertEquals(-(float) Math.PI / 2, layer.rotation());
    assertEquals(24, layer.width());
    assertEquals(16, layer.height());
    var custom =
        new ProgressElement(Common.empty(), Optional.of(A), ProgressDirection.UP).spriteElement();
    assertFalse(custom.rotate());
    assertEquals(0xff404040, custom.backgroundTint());
  }

  @Test
  void xmlSupportsSpritesAndStableFingerprints() throws Exception {
    String body =
        """
        <sprite sprite="test:a" width="17" height="11" tint="#55AA88"/>
        <animated-sprite frames="test:a test:b" width="17" height="11"/>
        <progress-sprite background="test:a" foreground="test:b" width="24" height="16" value="#getProgress" max="#getProgressMax" direction="left"/>
        """;
    var screen = read(body);
    var bound = ScreenBinder.bind(screen, getClass().getClassLoader());
    assertEquals(1, bound.values().size());
    assertInstanceOf(SpriteElement.class, screen.body().getFirst());
    var animation = assertInstanceOf(AnimatedSpriteElement.class, screen.body().get(1));
    assertTrue(animation.loop());
    assertEquals(1, animation.ticksPerFrame());
    assertEquals(screen.fingerprint(), read(body).fingerprint());
    assertNotEquals(
        screen.fingerprint(), read(body.replace("test:a test:b", "test:b test:a")).fingerprint());
    assertNotEquals(
        screen.fingerprint(),
        read(body.replace("direction=\"left\"", "direction=\"up\"")).fingerprint());
    assertThrows(
        ScreenException.class, () -> read("<sprite sprite=\"test:a\" width=\"0\" height=\"1\"/>"));
    assertThrows(
        ScreenException.class,
        () -> read("<animated-sprite frames=\"\" width=\"1\" height=\"1\"/>"));
    assertThrows(ScreenException.class, () -> read(body.replace(" max=\"#getProgressMax\"", "")));
  }

  @Test
  void outlineWrapsExactlyOneChildAndPreservesItsGeometry() throws Exception {
    var screen =
        read(
            """
            <hbox>
              <coloured-group id="group" color="#55AA88">
                <inventory id="inventory" storage="input" columns="2" spacing="2">
                  <item-slot id="one" index="0"/><ghost-slot id="two" index="1"/>
                </inventory>
              </coloured-group>
            </hbox>
            """);
    var outline = (ColouredGroupElement) screen.find("group").orElseThrow();
    var layout = ScreenLayout.of(screen, TextMeasure.NONE);
    var bounds = layout.bounds(outline);
    assertEquals(bounds, layout.bounds(outline.child()));
    assertEquals(38, bounds.width());
    assertEquals(18, bounds.height());
    assertEquals(bounds.x() + 20, layout.bounds(screen.find("two").orElseThrow()).x());
    assertEquals(new Rect(bounds.x() - 1, bounds.y() - 1, 40, 20), outline.outline(bounds, true));
    assertThrows(ScreenException.class, () -> read("<coloured-group color=\"#ffffff\"/>"));
    assertThrows(
        ScreenException.class,
        () -> read("<coloured-group color=\"#ffffff\"><spacer/><spacer/></coloured-group>"));
  }

  @Test
  void outlinePreservesStretchingAndContextualFillValidation() throws Exception {
    var screen =
        read(
            """
            <hbox>
              <sprite sprite="test:a" width="20" height="50"/>
              <coloured-group id="group" color="#ffffff"><gauge id="gauge" length="fill" value="#getEnergy" max="#getEnergyCapacity" color="#ffffff"/></coloured-group>
            </hbox>
            """);
    var layout = ScreenLayout.of(screen, TextMeasure.NONE);
    assertEquals(50, layout.bounds(screen.find("gauge").orElseThrow()).height());
    assertEquals(
        layout.bounds(screen.find("group").orElseThrow()),
        layout.bounds(screen.find("gauge").orElseThrow()));
  }
}
