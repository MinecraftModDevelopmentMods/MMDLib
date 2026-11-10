package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.model.containers.*;
import zone.moddev.mc.lib.gui.model.elements.*;

class PlayerElementsTest {
  private static final class ModPlayerSlot extends PlayerSlotElement {
    ModPlayerSlot(int index, int colour) {
      super(
          Common.empty(),
          index,
          new SlotElement.Appearance(
              SlotSize.NORMAL,
              Optional.of(colour),
              Optional.empty(),
              ResourceLocation.parse("mmdlib:slot"),
              .42f));
    }
  }

  @Test
  void playerFactoriesCreateStableChildrenIncludedInLayoutAndModelIdentity() {
    var calls = new ArrayList<Integer>();
    java.util.function.IntFunction<SlotElement> factory =
        index -> {
          calls.add(index);
          return new ModPlayerSlot(index, 0x4488aa);
        };
    var parts =
        List.of(
            new PlayerInventoryElement(Common.empty(), factory),
            new PlayerArmourElement(Common.empty(), factory),
            new PlayerOffhandElement(Common.empty(), factory));
    assertEquals(41, calls.size());
    assertEquals(41, new HashSet<>(calls).size());
    for (var part : parts) {
      var children = part.children();
      assertSame(children, part.children());
      for (var child : children) assertInstanceOf(ModPlayerSlot.class, child);
      var size = part.measure(TextMeasure.NONE);
      var positions = part.childLayout(new Rect(0, 0, size[0], size[1]), TextMeasure.NONE);
      assertEquals(children.size(), positions.size());
      for (var child : children) assertNotNull(positions.get(child));
    }
    assertEquals(41, calls.size());
    var same = new PlayerInventoryElement(Common.empty(), i -> new ModPlayerSlot(i, 0x4488aa));
    var different = new PlayerInventoryElement(Common.empty(), i -> new ModPlayerSlot(i, 0xaabbcc));
    assertEquals(parts.getFirst(), same);
    assertNotEquals(same, different);
    var id = ResourceLocation.parse("test:factories");
    var first =
        new Screen(
            id,
            "factory",
            new Text("test", Optional.empty()),
            "test.Host",
            true,
            List.of(same),
            List.of());
    var second =
        new Screen(id, "factory", first.title(), "test.Host", true, List.of(different), List.of());
    assertNotEquals(first.fingerprint(), second.fingerprint());
    assertThrows(
        IllegalArgumentException.class,
        () -> new PlayerOffhandElement(Common.empty(), i -> PlayerSlotElement.player(0)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PlayerOffhandElement(
                Common.empty(),
                i ->
                    new ItemSlotElement(
                        Common.empty(),
                        i,
                        SlotKind.NORMAL,
                        SlotElement.Appearance.standard(
                            SlotSize.NORMAL, Optional.empty(), Optional.empty()))));
  }

  private Screen read(String attributes, String body) throws Exception {
    return ScreenReader.read(
        ResourceLocation.parse("test:player"),
        "player.xml",
        new ByteArrayInputStream(
            ("<screen xmlns=\"https://moddev.zone/mmdlib/screen\" title=\"test\" block-entity=\""
                    + ElementInteractionTest.Host.class.getName()
                    + "\" "
                    + attributes
                    + ">"
                    + body
                    + "</screen>")
                .getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void defaultSectionsAreStableOrdinaryElementsWithMatchingSlotGeometry() throws Exception {
    var screen = read("", "<label text=\"test\"/>");
    assertEquals(
        List.of(
            PlayerInventoryElement.class, PlayerArmourElement.class, PlayerOffhandElement.class),
        screen.implicitElements().stream().map(Object::getClass).toList());
    var layout = ScreenLayout.of(screen, TextMeasure.NONE);
    var inventory = layout.region(PlayerInventoryElement.INVENTORY).orElseThrow();
    var slots =
        screen.elements().stream()
            .filter(PlayerSlotElement.class::isInstance)
            .map(PlayerSlotElement.class::cast)
            .toList();
    assertEquals(41, slots.size());
    assertEquals(41, slots.stream().map(SlotElement::index).distinct().count());
    for (var slot : slots) {
      assertSame(slot, screen.elements().stream().filter(e -> e == slot).findFirst().orElseThrow());
      assertEquals(PlayerInventoryElement.slot(inventory, slot.index()), layout.bounds(slot));
      assertEquals(
          List.of(),
          slot.associatedGroups(
              screen,
              List.of(
                  new ElementGroup(
                      "machine",
                      List.of(),
                      Optional.of(
                          new ElementGroup.Members(
                              zone.moddev.mc.lib.api.automation.ResourceType.ITEMS.id(),
                              "items",
                              List.of(slot.index())))))));
    }
  }

  @Test
  void eachSectionCanBePlacedIndependentlyIncludingParentOffsets() throws Exception {
    var screen =
        read(
            "",
            """
            <hbox offset-x="3" offset-y="7" spacing="6">
              <player-armour id="armour"/><player-inventory id="items"/><player-offhand id="offhand"/>
            </hbox>
            """);
    assertTrue(screen.implicitElements().isEmpty());
    var layout = ScreenLayout.of(screen, TextMeasure.NONE);
    var armour = screen.find("armour").orElseThrow();
    var items = screen.find("items").orElseThrow();
    var offhand = screen.find("offhand").orElseThrow();
    assertEquals(4, armour.children().size());
    assertEquals(36, items.children().size());
    assertEquals(1, offhand.children().size());
    assertEquals(162, layout.bounds(items).width());
    assertEquals(layout.bounds(armour).right() + 6, layout.bounds(items).x());
    assertEquals(layout.bounds(items).right() + 6, layout.bounds(offhand).x());
    assertEquals(layout.bounds(offhand), layout.bounds(offhand.children().getFirst()));
    assertEquals(layout.bounds(armour).y() + 54, layout.bounds(armour.children().getLast()).y());
  }

  @Test
  void missingSectionsFollowExplicitInventoryAndDuplicatesAreRejected() throws Exception {
    var screen = read("", "<player-inventory id=\"items\" offset-x=\"4\" offset-y=\"8\"/>");
    var layout = ScreenLayout.of(screen, TextMeasure.NONE);
    var inventory = layout.region(PlayerInventoryElement.INVENTORY).orElseThrow();
    assertEquals(2, screen.implicitElements().size());
    for (var element : screen.implicitElements()) {
      var part = (PlayerSlotsElement) element;
      assertEquals(part.defaultBounds(inventory), layout.bounds(part));
      assertSame(screen.find("items").orElseThrow(), part.visibilityAnchor(screen).orElseThrow());
    }
    for (var tag : List.of("player-inventory", "player-armour", "player-offhand")) {
      assertThrows(Exception.class, () -> read("", "<" + tag + "/><" + tag + "/>"));
      assertThrows(Exception.class, () -> read("inventory=\"none\"", "<" + tag + "/>"));
    }
    assertTrue(read("inventory=\"none\"", "<label text=\"test\"/>").implicitElements().isEmpty());
  }
}
