package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.client.*;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.*;

class WidgetInputTest {
  private static Element.Custom element(String id) {
    var c =
        new Common(
            Optional.of(id),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Common.Clicks.NONE,
            Optional.empty(),
            Optional.empty(),
            List.of(),
            0,
            0,
            1);
    return new Element.Custom(c, ResourceLocation.parse("test:editor"), 40, 20, Map.of());
  }

  private static final class Editor implements WidgetInstance {
    boolean focused;
    int clicks, drags, releases, cancels, removals, resizes, chars;

    @Override
    public void render(Context context) {}

    @Override
    public boolean focusable() {
      return true;
    }

    @Override
    public void setFocused(boolean value) {
      focused = value;
    }

    @Override
    public Click mouseClicked(double x, double y, int button) {
      clicks++;
      return Click.CAPTURE;
    }

    @Override
    public boolean mouseDragged(double x, double y, int b, double dx, double dy) {
      drags++;
      return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int b) {
      releases++;
      return true;
    }

    @Override
    public void cancelInteraction() {
      cancels++;
    }

    @Override
    public void removed() {
      removals++;
    }

    @Override
    public void resized() {
      resizes++;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
      chars++;
      return true;
    }
  }

  private static final class Fixture {
    final Element.Custom a = element("a"), b = element("b");
    final Editor first = new Editor(), second = new Editor();
    final Set<Element.Custom> hidden = Collections.newSetFromMap(new IdentityHashMap<>());
    final WidgetInput input =
        new WidgetInput(
            List.of(a, b),
            e -> e == a ? first : second,
            e -> !hidden.contains(e),
            e -> new Rect(e == a ? 0 : 50, 0, 40, 20));

    Fixture() {
      input.update();
    }
  }

  @Test
  void inventoryReleaseOverWidgetIsNotDeliveredWithoutCapture() {
    var f = new Fixture();
    f.input.beginClick(); // The press belongs to an inventory slot.
    assertFalse(f.input.release(5, 5, 0));
    assertFalse(f.input.release(55, 5, 1));
    assertEquals(0, f.first.releases);
    assertEquals(0, f.second.releases);
    assertTrue(f.input.click(f.a, 5, 5, 0));
    assertTrue(f.input.release(55, 5, 0));
    assertEquals(1, f.first.releases);
    assertEquals(0, f.second.releases);
  }

  @Test
  void capturedReleaseOutsideBoundsDoesNotLeakToSlots() {
    var f = new Fixture();
    assertTrue(f.input.click(f.a, 5, 5, 0));
    assertTrue(f.input.drag(900, -50, 0, 895, -55));
    assertTrue(f.input.release(900, -50, 0));
    assertEquals(1, f.first.drags);
    assertEquals(1, f.first.releases);
    assertFalse(f.input.release(900, -50, 0));
  }

  @Test
  void hidingCancelsDragAndFocusAndConsumesTheOutstandingRelease() {
    var f = new Fixture();
    f.input.click(f.a, 5, 5, 0);
    f.hidden.add(f.a);
    f.input.update();
    assertFalse(f.first.focused);
    assertEquals(1, f.first.cancels);
    assertTrue(f.input.release(60, 5, 0));
    assertEquals(0, f.first.releases);
    assertFalse(f.input.charTyped('e', 0));
    assertFalse(f.input.click(f.a, 5, 5, 0));
  }

  @Test
  void resizePreservesInstanceAndFocusButCancelsDrag() {
    var f = new Fixture();
    f.input.click(f.a, 5, 5, 0);
    f.input.resized();
    assertSame(f.first, f.input.instance(f.a));
    assertTrue(f.first.focused);
    assertEquals(1, f.first.resizes);
    assertEquals(1, f.first.cancels);
    assertTrue(f.input.release(5, 5, 0));
    assertEquals(0, f.first.releases);
  }

  @Test
  void tabSkipsHiddenWidgetsAndFocusedEditorsBlockInventoryKeys() {
    var f = new Fixture();
    assertTrue(f.input.keyPressed(258, 0, 0));
    assertTrue(f.first.focused);
    assertTrue(f.input.keyPressed(69, 0, 0)); // Unhandled E must not close inventory.
    assertTrue(f.input.charTyped('e', 0));
    assertEquals(1, f.first.chars);
    assertTrue(f.input.keyPressed(258, 0, 0));
    assertFalse(f.first.focused);
    assertTrue(f.second.focused);
    assertTrue(f.input.keyPressed(258, 0, 1));
    assertTrue(f.first.focused);
    f.hidden.add(f.b);
    f.input.update();
    f.input.keyPressed(258, 0, 0);
    assertSame(f.a, f.input.focused());
    assertTrue(f.input.keyPressed(256, 0, 0));
    assertNull(f.input.focused());
    assertFalse(f.input.keyPressed(69, 0, 0));
  }

  @Test
  void closingIsIdempotentAndFactoriesCannotShareAnInstance() {
    var f = new Fixture();
    f.input.click(f.a, 5, 5, 0);
    f.input.removed();
    f.input.removed();
    assertEquals(1, f.first.cancels);
    assertEquals(1, f.first.removals);
    assertEquals(1, f.second.removals);
    assertFalse(f.first.focused);
    assertFalse(f.input.click(f.a, 5, 5, 0));
    assertFalse(f.input.keyPressed(258, 0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new WidgetInput(
                List.of(f.a, f.b), e -> f.first, e -> true, e -> new Rect(0, 0, 40, 20)));
  }

  @Test
  void routingReusesOneVisibilitySnapshotPerEvent() {
    var element = element("counted");
    var reads = new java.util.concurrent.atomic.AtomicInteger();
    var input =
        new WidgetInput(
            List.of(element),
            e -> new Editor(),
            e -> {
              reads.incrementAndGet();
              return true;
            },
            e -> new Rect(0, 0, 40, 20));
    input.update();
    input.capturing();
    input.beginClick();
    input.click(element, 5, 5, 0);
    input.tick();
    input.keyPressed(258, 0, 0);
    assertEquals(1, reads.get());
    input.update();
    assertEquals(2, reads.get());
  }

  @Test
  void cancelledReleaseDoesNotSwallowANewInventoryGesture() {
    var f = new Fixture();
    f.input.click(f.a, 5, 5, 0);
    f.input.resized();
    f.input.beginClick(); // A later press on an inventory slot.
    assertFalse(f.input.release(300, 300, 0));
  }
}
