package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.gui.layout.GroupHighlights;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.model.containers.*;
import zone.moddev.mc.lib.gui.model.elements.*;

class GroupHighlightsTest {
  private record Custom(String name, List<String> groups, List<Element> children, boolean replaces)
      implements Element {
    public Common common() {
      var c = Common.empty();
      return new Common(
          Optional.ofNullable(name),
          c.value(),
          c.max(),
          c.tooltip(),
          c.tooltipShift(),
          c.clicks(),
          c.onHover(),
          c.hoverShows(),
          c.jeiRecipes(),
          0,
          0,
          0);
    }

    public List<String> highlightedGroups(Screen screen, List<ElementGroup> groups) {
      return this.groups;
    }

    public boolean replacesChildHighlight(
        Screen screen, Element descendant, String group, List<ElementGroup> groups) {
      return replaces;
    }
  }

  private Screen screen(List<Element> body) {
    return new Screen(
        ResourceLocation.parse("test:highlights"),
        "highlights",
        new Text("test", Optional.empty()),
        "test.Host",
        false,
        body,
        List.of());
  }

  @Test
  void onlySelectedEligibleAncestorsReplaceDescendantsPerGroup() {
    var child = new Custom("child", List.of("a", "b", "b", "unknown"), List.of(), false);
    var wrapper = new Custom(null, List.of(), List.of(child), true);
    var parent = new Custom("parent", List.of("a"), List.of(wrapper), true);
    var sibling = new Custom("sibling", List.of("a"), List.of(), false);
    var screen = screen(List.of(parent, sibling));
    var groups = List.of(new ElementGroup("a", List.of()), new ElementGroup("b", List.of()));
    var targets = GroupHighlights.of(screen, groups);
    assertEquals(List.of(parent, sibling), targets.get("a"));
    assertEquals(List.of(child), targets.get("b"));
    assertThrows(UnsupportedOperationException.class, () -> targets.clear());
    assertThrows(UnsupportedOperationException.class, () -> targets.get("a").clear());
    var anonymous = new Custom(null, List.of("a"), List.of(child), true);
    assertEquals(
        List.of(child),
        GroupHighlights.of(screen(List.of(anonymous)), groups, e -> e.common().id().isPresent())
            .get("a"));
  }

  @Test
  void scopedAndLegacyMembersStayDistinctAndNestedScopesKeepOwnHighlights() {
    var appearance =
        SlotElement.Appearance.standard(SlotSize.NORMAL, Optional.empty(), Optional.empty());
    var local = new ItemSlotElement(Common.empty(), 0, SlotKind.NORMAL, appearance);
    var legacy = new ItemSlotElement(Common.empty(), 3, SlotKind.NORMAL, appearance);
    var wrong = new ItemSlotElement(Common.empty(), 0, SlotKind.NORMAL, appearance);
    var nested = new ItemSlotElement(Common.empty(), 0, SlotKind.NORMAL, appearance);
    class Scope extends BoxElement implements InventoryScope {
      private final String name;

      Scope(String name, List<Element> children) {
        super(Common.empty(), true, 0, 0, Align.START, children);
        this.name = name;
      }

      public String inventory() {
        return name;
      }
    }
    var outline =
        new ColouredGroupElement(
            Common.empty(),
            0xffffff,
            new Scope("input", List.of(local, new Scope("nested", List.of(nested)))));
    var groups =
        List.of(
            new ElementGroup(
                "input",
                List.of(),
                Optional.of(
                    new ElementGroup.Members(
                        ResourceType.ITEMS.id(), "input", List.of(0), List.of(3)))),
            new ElementGroup(
                "nested",
                List.of(),
                Optional.of(
                    new ElementGroup.Members(
                        ResourceType.ITEMS.id(), "nested", List.of(0), List.of(4)))));
    var screen = screen(List.of(outline, legacy, wrong));
    var targets = GroupHighlights.of(screen, groups);
    assertEquals(List.of(outline, legacy), targets.get("input"));
    assertEquals(List.of(nested), targets.get("nested"));
  }

  @Test
  void resourceAssociationsSupportCustomKindsAndExplicitAnchors() {
    Element custom =
        new Element() {
          public Common common() {
            return Common.empty();
          }

          public Optional<ResourceBinding> resourceBinding() {
            return Optional.of(new ResourceBinding("mod:pressure", 2));
          }
        };
    var groups =
        List.of(
            new ElementGroup(
                "pressure",
                List.of(),
                Optional.of(
                    new ElementGroup.Members(
                        ResourceLocation.parse("mod:pressure"), "vessel", List.of(2)))),
            new ElementGroup(
                "anchored",
                List.of("elsewhere"),
                Optional.of(
                    new ElementGroup.Members(
                        ResourceLocation.parse("mod:pressure"), "vessel", List.of(2)))));
    var screen = screen(List.of(custom));
    assertEquals(List.of("pressure"), custom.associatedGroups(screen, groups));
    assertEquals(List.of(custom), GroupHighlights.of(screen, groups).get("pressure"));
    assertTrue(GroupHighlights.of(screen, groups).get("anchored").isEmpty());
  }
}
