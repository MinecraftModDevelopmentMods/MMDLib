package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.gui.layout.GroupOrder;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.*;

class GroupOrderTest {
  private Screen read(String body) throws Exception {
    return ScreenReader.read(
        ResourceLocation.parse("test:groups"),
        "groups.xml",
        new ByteArrayInputStream(
            ("<screen xmlns=\"https://moddev.zone/mmdlib/screen\" title=\"test\" block-entity=\""
                    + ElementInteractionTest.Host.class.getName()
                    + "\">"
                    + body
                    + "</screen>")
                .getBytes(StandardCharsets.UTF_8)));
  }

  private ElementGroup member(String id, String inventory, String... anchors) {
    return new ElementGroup(
        id,
        List.of(anchors),
        Optional.of(new ElementGroup.Members(ResourceType.ITEMS.id(), inventory, List.of(0))));
  }

  @Test
  void slotsOwnNamedMembershipAndExplicitAnchorsTakePrecedence() throws Exception {
    var screen =
        read(
            """
            <inventory storage="input"><item-slot id="input_slot" index="0"/></inventory>
            <inventory storage="output"><ghost-slot id="output_slot" index="0"/></inventory>
            <slot id="legacy" index="0"/>
            """);
    var groups =
        List.of(
            member("output", "output"),
            member("input", "input"),
            member("anchored", "input", "output_slot"),
            member("absent", "input", "missing"),
            new ElementGroup(
                "fluid",
                List.of(),
                Optional.of(
                    new ElementGroup.Members(ResourceType.FLUIDS.id(), "input", List.of(0)))));
    assertEquals(
        List.of("input"), screen.find("input_slot").orElseThrow().associatedGroups(screen, groups));
    assertEquals(
        List.of("output", "anchored"),
        screen.find("output_slot").orElseThrow().associatedGroups(screen, groups));
    assertEquals(
        List.of("output", "input"),
        screen.find("legacy").orElseThrow().associatedGroups(screen, groups));
    assertEquals(
        List.of("input", "output", "anchored", "absent", "fluid"),
        GroupOrder.of(
            screen,
            groups.stream().map(ElementGroup::id).toList(),
            e -> e.associatedGroups(screen, groups)));
  }

  private record ModElement(Common common, List<String> associations) implements Element {
    public int[] measure(TextMeasure text) {
      return new int[] {10, 10};
    }

    public List<String> associatedGroups(Screen screen, List<ElementGroup> groups) {
      return associations;
    }
  }

  @Test
  void customElementsChooseAssociationsWithoutChangingSharedOrdering() throws Exception {
    var original = read("<label text=\"test\"/>");
    var screen =
        new Screen(
            original.id(),
            original.source(),
            original.title(),
            original.blockEntity(),
            false,
            List.of(
                new ModElement(Common.empty(), List.of("c", "unknown", "b", "c")),
                new ModElement(Common.empty(), List.of("b", "a"))),
            List.of());
    assertEquals(
        List.of("c", "b", "a", "d"),
        GroupOrder.of(
            screen, List.of("a", "b", "c", "d"), e -> e.associatedGroups(screen, List.of())));
    assertEquals(List.of(3, 1, 2), GroupOrder.of(screen, List.of(1, 2, 3), e -> List.of(3)));
    assertEquals(
        List.of("c", "unknown", "b", "c"),
        screen.body().getFirst().highlightedGroups(screen, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> GroupOrder.of(screen, List.of("a", "a"), e -> List.of()));
  }

  @Test
  void highlightingCanOptOutWithoutChangingOrderingAssociations() throws Exception {
    var screen = read("<label text=\"test\"/>");
    Element element =
        new Element() {
          public Common common() {
            return Common.empty();
          }

          public int[] measure(TextMeasure text) {
            return new int[] {1, 1};
          }

          public List<String> associatedGroups(Screen s, List<ElementGroup> groups) {
            return List.of("group");
          }

          public List<String> highlightedGroups(Screen s, List<ElementGroup> groups) {
            return List.of();
          }
        };
    assertEquals(List.of("group"), element.associatedGroups(screen, List.of()));
    assertTrue(element.highlightedGroups(screen, List.of()).isEmpty());
  }
}
