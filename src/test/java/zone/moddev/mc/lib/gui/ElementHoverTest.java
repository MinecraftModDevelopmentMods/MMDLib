package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.client.*;
import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.model.*;

class ElementHoverTest {
  private record Leaf(Common common) implements Element {}

  private static class Fixture {
    final Leaf first = new Leaf(Common.empty()), last = new Leaf(Common.empty());
    final Set<Element> hidden = Collections.newSetFromMap(new IdentityHashMap<>());
    final Map<Element, Rect> bounds = new IdentityHashMap<>();
    final List<String> events = new ArrayList<>();
    Rect viewport = new Rect(0, 0, 20, 20);
    final ElementInput<Leaf, ElementBehavior> input;

    Fixture() {
      bounds.put(first, new Rect(0, 0, 30, 30));
      bounds.put(last, new Rect(0, 0, 30, 30));
      input =
          new ElementInput<>(
              List.of(first, last),
              e ->
                  new ElementBehavior() {
                    public void hoverChanged(boolean entered) {
                      events.add(name(e) + ":" + entered);
                    }

                    public void removed() {
                      events.add(name(e) + ":removed");
                    }
                  },
              e -> !hidden.contains(e),
              bounds::get,
              (x, y) -> viewport.contains(x, y),
              (e, entered) -> events.add(name(e) + ":host:" + entered));
      input.update();
    }

    String name(Element e) {
      return e == first ? "first" : "last";
    }
  }

  @Test
  void topmostIdentityReceivesOneTransitionAcrossFramesAndMouseEvents() {
    var f = new Fixture();
    assertEquals(f.first, f.last); // Distinct instances with equal model data.
    f.input.hover(5, 5);
    f.input.hover(5, 5);
    f.input.mouseMoved(6, 6);
    f.input.update();
    assertSame(f.last, f.input.hovered());
    assertEquals(List.of("last:true", "last:host:true"), f.events);
    f.hidden.add(f.last);
    f.input.update();
    assertSame(f.first, f.input.hovered());
    assertEquals(
        List.of(
            "last:true",
            "last:host:true",
            "last:false",
            "last:host:false",
            "first:true",
            "first:host:true"),
        f.events);
  }

  @Test
  void viewportAndGeometryChangesRetargetAStationaryPointer() {
    var f = new Fixture();
    f.input.hover(25, 5); // Inside element bounds, outside fragment viewport.
    assertNull(f.input.hovered());
    f.viewport = new Rect(0, 0, 30, 30);
    f.input.update();
    assertSame(f.last, f.input.hovered());
    f.bounds.put(f.last, new Rect(40, 40, 30, 30));
    f.input.resized();
    assertSame(f.first, f.input.hovered());
    f.viewport = new Rect(0, 0, 0, 0);
    f.input.update();
    assertNull(f.input.hovered());
    assertEquals(8, f.events.size());
  }

  @Test
  void removalLeavesBeforeDisposalAndCannotReenter() {
    var f = new Fixture();
    f.input.hover(5, 5);
    f.input.removed();
    var events = List.copyOf(f.events);
    assertTrue(events.indexOf("last:false") < events.indexOf("last:removed"));
    f.input.removed();
    f.input.hover(5, 5);
    f.input.mouseMoved(5, 5);
    f.input.update();
    f.input.resized();
    assertEquals(events, f.events);
    assertNull(f.input.hovered());
  }

  @Test
  void suspensionRequiresANewPointerSample() {
    var f = new Fixture();
    f.input.hover(5, 5);
    f.input.clearHover();
    f.input.update();
    assertNull(f.input.hovered());
    f.input.hover(5, 5);
    assertSame(f.last, f.input.hovered());
    assertEquals(
        List.of(
            "last:true",
            "last:host:true",
            "last:false",
            "last:host:false",
            "last:true",
            "last:host:true"),
        f.events);
  }
}
