package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.load.ResourceValidationContext;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.model.containers.InventoryElement;
import zone.moddev.mc.lib.gui.model.elements.*;

class ResourceValidationTest {
  private static final SlotElement.Appearance APPEARANCE =
      SlotElement.Appearance.standard(SlotSize.NORMAL, Optional.empty(), Optional.empty());

  private Screen screen(List<Element> elements) {
    return new Screen(
        ResourceLocation.parse("test:resources"),
        "resources",
        new Text("test", Optional.empty()),
        "test.Host",
        false,
        elements,
        List.of());
  }

  @Test
  void slotRulesUseNamedCountsAndVirtualGhostPolicy() {
    var real = new ItemSlotElement(Common.empty(), 1, SlotKind.NORMAL, APPEARANCE);
    var ghost =
        new ItemSlotElement(Common.empty(), 99, SlotKind.NORMAL, APPEARANCE) {
          public boolean ghost() {
            return true;
          }
        };
    var player = new PlayerSlotElement(Common.empty(), 9, APPEARANCE);
    var screen =
        screen(
            List.of(
                new InventoryElement(Common.empty(), "input", 1, 0, List.of(real)), ghost, player));
    var context =
        new ResourceValidationContext(screen, Map.of("items", 20), Map.of("input", 1), Map.of());
    assertThrows(IllegalArgumentException.class, () -> real.validateResources(context));
    assertDoesNotThrow(() -> ghost.validateResources(context));
    assertDoesNotThrow(() -> player.validateResources(context));
    assertDoesNotThrow(
        () ->
            real.validateResources(
                new ResourceValidationContext(
                    screen, Map.of("items", 0), Map.of("input", 2), Map.of())));
  }

  @Test
  void customResourcesAndExistingHooksReceiveImmutableContext() {
    int[] calls = {0, 0};
    Element custom =
        new Element() {
          public Common common() {
            return Common.empty();
          }

          public Optional<ResourceBinding> resourceBinding() {
            return Optional.of(new ResourceBinding("mod:pressure", 1));
          }

          public void validateBindings(Map<String, Set<String>> bindings) {
            calls[0]++;
            assertEquals(Set.of("pump"), bindings.get("mod:devices"));
          }

          public void validateResources(Map<String, Integer> counts) {
            calls[1]++;
            Element.super.validateResources(counts);
          }
        };
    var resources = new HashMap<>(Map.of("mod:pressure", 2));
    var devices = new HashSet<>(Set.of("pump"));
    var context =
        new ResourceValidationContext(
            screen(List.of(custom)), resources, Map.of(), Map.of("mod:devices", devices));
    resources.clear();
    devices.clear();
    custom.validateResources(context);
    assertArrayEquals(new int[] {1, 1}, calls);
    assertThrows(UnsupportedOperationException.class, () -> context.resources().clear());
    assertThrows(
        UnsupportedOperationException.class, () -> context.bindings().get("mod:devices").clear());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            custom.validateResources(
                new ResourceValidationContext(
                    context.screen(), Map.of("mod:pressure", 1), Map.of(), context.bindings())));
  }
}
