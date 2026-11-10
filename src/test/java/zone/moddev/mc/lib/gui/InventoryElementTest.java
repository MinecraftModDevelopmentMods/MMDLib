package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.model.containers.AutomationInventoryElement;
import zone.moddev.mc.lib.gui.model.containers.InventoryElement;
import zone.moddev.mc.lib.gui.model.elements.GhostSlotElement;
import zone.moddev.mc.lib.gui.model.elements.ItemSlotElement;

class InventoryElementTest {
  private Screen read(String body) throws Exception {
    String xml =
        "<screen xmlns=\"https://moddev.zone/mmdlib/screen\" title=\"test\" block-entity=\""
            + ElementInteractionTest.Host.class.getName()
            + "\">"
            + body
            + "</screen>";
    return ScreenReader.read(
        ResourceLocation.parse("test:inventory"),
        "inventory.xml",
        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void localIndexesAndSharedAppearance() throws Exception {
    var screen =
        read(
            """
            <inventory storage="input" columns="2" spacing="3">
              <item-slot index="0" size="large" background="test:frame" icon="test:ore"/>
              <ghost-slot index="1" ghost-alpha="0.25"/>
              <item-slot index="2"/>
            </inventory>
            <inventory storage="output"><item-slot index="0"/></inventory>
            """);
    var inventory = (InventoryElement) screen.body().getFirst();
    assertInstanceOf(ItemSlotElement.class, inventory.slots().getFirst());
    var ghost = assertInstanceOf(GhostSlotElement.class, inventory.slots().get(1));
    assertEquals(.25f, ghost.appearance().ghostAlpha());
    assertEquals(
        ResourceLocation.parse("test:frame"),
        inventory.slots().getFirst().appearance().background());
    assertEquals(new Rect(29, 0, 18, 18), inventory.positions().get(ghost));
    assertEquals(new Rect(0, 29, 18, 18), inventory.positions().get(inventory.slots().get(2)));
    assertArrayEquals(new int[] {47, 47}, inventory.dimensions());
    assertEquals(
        "output",
        InventoryScope.name(screen, ((InventoryElement) screen.body().get(1)).slots().getFirst()));
  }

  @Test
  void rejectsDuplicateBindingsAndInvalidChildren() {
    assertThrows(
        Exception.class,
        () ->
            read(
                "<inventory storage=\"input\"><item-slot index=\"0\"/><ghost-slot"
                    + " index=\"0\"/></inventory>"));
    assertThrows(
        Exception.class,
        () -> read("<inventory storage=\"input\"><label text=\"bad\"/></inventory>"));
    assertThrows(Exception.class, () -> read("<ghost-slot index=\"0\" ghost-alpha=\"2\"/>"));
  }

  @Test
  void playerAndAddonPresetsUseSharedSlots() {
    var inventory =
        new zone.moddev.mc.lib.gui.model.containers.PlayerInventoryElement(Common.empty());
    var armour = new zone.moddev.mc.lib.gui.model.containers.PlayerArmourElement(Common.empty());
    var offhand = new zone.moddev.mc.lib.gui.model.containers.PlayerOffhandElement(Common.empty());
    assertEquals(36, inventory.children().size());
    assertEquals(4, armour.children().size());
    assertEquals(1, offhand.children().size());
    var equipment =
        java.util.stream.Stream.concat(armour.children().stream(), offhand.children().stream())
            .map(zone.moddev.mc.lib.gui.model.elements.SlotElement.class::cast)
            .toList();
    assertTrue(equipment.stream().allMatch(s -> s.icon().isPresent()));
    var addon = new AutomationInventoryElement("upgrades");
    assertEquals(8, addon.slots().size());
    assertEquals(12, addon.slots().get(4).appearance().itemSize());
    assertEquals(new Rect(2, 22, 14, 14), addon.slotBounds(4));
  }

  private record Scope(String inventory, java.util.List<Element> children)
      implements InventoryScope {
    public Common common() {
      return Common.empty();
    }
  }

  private Screen model(
      java.util.List<Element> body,
      java.util.List<Element> tabs,
      java.util.List<Element> implicit) {
    return new Screen(
        ResourceLocation.parse("test:scope"),
        "scope",
        new Text("test", java.util.Optional.empty()),
        "test.Host",
        false,
        body,
        tabs,
        java.util.Map.of(),
        implicit);
  }

  @Test
  void customScopesUseNearestAncestorAcrossWrappersAndAllScreenRoots() {
    var appearance =
        zone.moddev.mc.lib.gui.model.elements.SlotElement.Appearance.standard(
            SlotSize.NORMAL, java.util.Optional.empty(), java.util.Optional.empty());
    var a = new ItemSlotElement(Common.empty(), 0, SlotKind.NORMAL, appearance);
    var b = new ItemSlotElement(Common.empty(), 0, SlotKind.NORMAL, appearance);
    assertEquals(a, b); // Equal model values must still resolve by identity.
    var wrapper =
        new zone.moddev.mc.lib.gui.model.containers.BoxElement(
            Common.empty(), true, 0, 0, Align.START, java.util.List.of(a));
    var inner = new Scope("inner", java.util.List.of(b));
    var outer = new Scope("outer", java.util.List.of(wrapper, inner));
    for (int root = 0; root < 3; root++) {
      var screen =
          model(
              root == 0 ? java.util.List.of(outer) : java.util.List.of(),
              root == 1 ? java.util.List.of(outer) : java.util.List.of(),
              root == 2 ? java.util.List.of(outer) : java.util.List.of());
      assertSame(outer, InventoryScope.owner(screen, a).orElseThrow());
      assertSame(inner, InventoryScope.owner(screen, b).orElseThrow());
      var context = new zone.moddev.mc.lib.gui.load.ScreenValidationContext(screen);
      assertDoesNotThrow(
          () -> {
            a.validateScreen(context);
            b.validateScreen(context);
          });
      var groups =
          java.util.List.of(
              new ElementGroup(
                  "outer_group",
                  java.util.List.of(),
                  java.util.Optional.of(
                      new ElementGroup.Members(
                          zone.moddev.mc.lib.api.automation.ResourceType.ITEMS.id(),
                          "outer",
                          java.util.List.of(0)))));
      assertEquals(java.util.List.of("outer_group"), a.associatedGroups(screen, groups));
      assertTrue(b.associatedGroups(screen, groups).isEmpty());
    }
    assertEquals(
        "items",
        InventoryScope.name(
            model(java.util.List.of(a), java.util.List.of(), java.util.List.of()), a));
    var duplicate =
        model(
            java.util.List.of(
                new Scope("same", java.util.List.of(a)), new Scope("same", java.util.List.of(b))),
            java.util.List.of(),
            java.util.List.of());
    var validation = new zone.moddev.mc.lib.gui.load.ScreenValidationContext(duplicate);
    a.validateScreen(validation);
    assertThrows(IllegalArgumentException.class, () -> b.validateScreen(validation));
  }

  @Test
  void automationFactoriesPreserveBackingAndOwnDefaultRoles() {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var view =
        new AutomationInventoryElement(
            "addons",
            index -> {
              calls.incrementAndGet();
              return AutomationInventoryElement.defaultSlot(index);
            });
    assertEquals(8, calls.get());
    assertSame(view.slots().getFirst(), view.slots().getFirst());
    assertTrue(
        view.slots().stream()
            .allMatch(
                slot ->
                    slot.role() == zone.moddev.mc.lib.gui.runtime.slot.MenuSlots.Role.AUXILIARY));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AutomationInventoryElement(
                "addons", index -> AutomationInventoryElement.defaultSlot(0)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AutomationInventoryElement(
                "addons",
                index ->
                    new GhostSlotElement(
                        Common.empty(), index, view.slots().getFirst().appearance())));
  }
}
