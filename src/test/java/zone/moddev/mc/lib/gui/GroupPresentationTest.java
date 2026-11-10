package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import net.minecraft.client.gui.Font;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.client.*;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.model.containers.*;
import zone.moddev.mc.lib.gui.model.elements.LabelElement;

class GroupPresentationTest {
  private static final Text TOOLTIP = new Text("test.tooltip", Optional.of("Tooltip"));
  private static final Text CAPTION = new Text("test.caption", Optional.of("Caption"));

  private static Common common() {
    var c = Common.empty();
    return new Common(
        c.id(),
        c.value(),
        c.max(),
        Optional.of(TOOLTIP),
        c.tooltipShift(),
        c.clicks(),
        c.onHover(),
        c.hoverShows(),
        c.jeiRecipes(),
        0,
        0,
        0);
  }

  private static ElementRenderAccess access(ElementBehavior behavior, boolean visible) {
    return new ElementRenderAccess() {
      public Font font() {
        return null;
      }

      public Rect viewport() {
        return new Rect(0, 0, 100, 100);
      }

      public boolean visible(Element element) {
        return visible;
      }

      public <T> Optional<T> capability(Element element, Class<T> type) {
        return BehaviorCapabilities.find(behavior, type);
      }
    };
  }

  @Test
  void builtInCaptionsAndPlainTooltipFallbackKeepTheirSemantics() {
    var registry = ElementBehaviors.builtins();
    var group = new GroupElement(common(), Optional.of(CAPTION), Optional.empty(), 2, List.of());
    var behavior = registry.create(new ElementBehaviorsTest.Access(group));
    assertEquals(Optional.of(CAPTION), access(behavior, true).groupLabel(group));
    var unlabelled = new GroupElement(common(), Optional.empty(), Optional.empty(), 2, List.of());
    assertTrue(
        access(registry.create(new ElementBehaviorsTest.Access(unlabelled)), true)
            .groupLabel(unlabelled)
            .isEmpty(),
        "An explicit empty group caption does not become its tooltip");
    var plain = new LabelElement(common(), CAPTION);
    assertEquals(
        Optional.of(TOOLTIP),
        access(new DefaultElementBehavior(new ElementBehaviorsTest.Access(plain)), true)
            .groupLabel(plain));
  }

  @Test
  void customCapabilitiesWorkThroughAdaptersAndHiddenTargetsAreNotDrawn() {
    var plain = new LabelElement(common(), TOOLTIP);
    class Adapter implements ElementBehavior, BehaviorCapabilities {
      public <T> Optional<T> capability(Class<T> type) {
        if (type == GroupLabelProvider.class)
          return Optional.of(type.cast((GroupLabelProvider) () -> Optional.of(CAPTION)));
        if (type == HighlightPresentation.class)
          return Optional.of(
              type.cast(
                  (HighlightPresentation)
                      (g, b, r) -> {
                        fail("Hidden elements must not reach the highlight provider");
                      }));
        return Optional.empty();
      }
    }
    var access = access(new Adapter(), false);
    assertEquals(Optional.of(CAPTION), access.groupLabel(plain));
    assertDoesNotThrow(() -> access.highlight(null, plain, ElementHighlight.emphasis(0xff123456)));
  }

  @Test
  void fallbackGeometryAndOwnedDecorationRemainDistinct() {
    var bounds = new Rect(10, 20, 30, 40);
    assertEquals(bounds, ElementHighlight.decoration(0xff123456).outline(bounds));
    assertEquals(bounds, ElementHighlight.selection(0xff123456).outline(bounds));
    assertEquals(new Rect(9, 19, 32, 42), ElementHighlight.emphasis(0xff123456).outline(bounds));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ElementHighlight(ElementHighlight.Kind.EMPHASIS, 0, -1));
    var group =
        new ColouredGroupElement(
            Common.empty(), 0xffabcdef, new LabelElement(Common.empty(), CAPTION));
    var behavior = ElementBehaviors.builtins().create(new ElementBehaviorsTest.Access(group));
    var presentation =
        BehaviorCapabilities.find(behavior, HighlightPresentation.class).orElseThrow();
    assertDoesNotThrow(
        () -> presentation.highlight(null, bounds, ElementHighlight.decoration(0xff123456)),
        "Owned overlay decoration must not be drawn again by another feature");
  }
}
