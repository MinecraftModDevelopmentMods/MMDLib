package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.model.*;
import zone.moddev.mc.lib.gui.model.containers.SpacerElement;

class FilterElementsTest {
  record ModControl(String id) implements FilterControl {
    public int[] measure(TextMeasure text) {
      return new int[] {22, 13};
    }

    public boolean edit(Edit edit) {
      if (!edit.value().equals("accept")) return false;
      edit.configuration().putBoolean(id, true);
      return true;
    }
  }

  static final class Filter implements InventoryFilter {
    final FilterControl root;
    CompoundTag saved = new CompoundTag();
    boolean valid = true;

    Filter(FilterControl root) {
      this.root = root;
    }

    public ResourceType<?> resource(ItemStack stack) {
      return ResourceType.ITEMS;
    }

    public ResourceLocation editorType(ItemStack stack) {
      return ResourceLocation.parse("test:filter");
    }

    public FilterControl controls(ItemStack stack) {
      return root;
    }

    public boolean matches(ItemStack filter, Object resource, HolderLookup.Provider registries) {
      return true;
    }

    public CompoundTag configuration(ItemStack stack) {
      return saved.copy();
    }

    public void configure(ItemStack stack, CompoundTag value) {
      saved = value.copy();
    }

    public String sample(ItemStack filter, ItemStack cursor, HolderLookup.Provider registries) {
      return "minecraft:stone";
    }

    public boolean validate(
        ItemStack filter, CompoundTag candidate, HolderLookup.Provider registries) {
      return valid;
    }

    boolean edit(String id, int entry, String value) {
      return edit(null, id, entry, value, null, null);
    }
  }

  @Test
  void wrappingUsesModElementMinimumWidths() {
    var first = new ModControl("first");
    var second = new ModControl("second");
    var layout =
        ElementFragmentLayout.of(
            new FilterControl.Box("row", true, List.of(first, second)), 40, TextMeasure.NONE);
    assertEquals(22, layout.bounds().get(first).width());
    assertEquals(15, layout.bounds().get(second).y());
    assertEquals(28, layout.height());
  }

  @Test
  void modElementsComposeWithOrdinaryElementsAndOwnTheirValidation() {
    var control = new ModControl("mod");
    var root =
        new FilterControl.Box("root", false, List.of(new SpacerElement(Common.empty()), control));
    var layout = ElementFragmentLayout.of(root, 80, TextMeasure.NONE);
    assertEquals(80, layout.bounds().get(control).width());
    assertEquals(13, layout.bounds().get(control).height());
    assertEquals(List.of(root, control), FilterControl.flatten(root));
    assertEquals("mod", control.common().id().orElseThrow());
    var filter = new Filter(root);
    assertFalse(filter.edit("missing", 0, "accept"));
    assertFalse(filter.edit("mod", 0, "untrusted"));
    assertTrue(filter.saved.isEmpty());
    filter.valid = false;
    assertFalse(filter.edit("mod", 0, "accept"));
    assertTrue(filter.saved.isEmpty(), "Whole-filter validation runs before commit");
    filter.valid = true;
    assertTrue(filter.edit("mod", 0, "accept"));
    assertTrue(filter.saved.getBoolean("mod"));
  }

  @Test
  void sampleGeometryOwnsMeasurementAndHitTesting() {
    var samples =
        new FilterControl.Samples("samples", 5, ResourceType.ITEMS) {
          public int cellSize() {
            return 30;
          }

          public Rect sampleBounds(Rect bounds, int index) {
            var cell = super.sampleBounds(bounds, index);
            return new Rect(cell.x() + 2, cell.y() + 2, 26, 26);
          }
        };
    assertArrayEquals(new int[] {100, 58}, samples.measure(TextMeasure.NONE, 100));
    var bounds = new Rect(11, 23, 100, 58);
    assertEquals(new Rect(43, 55, 26, 26), samples.sampleBounds(bounds, 4));
    assertEquals(4, samples.sampleIndex(bounds, 44, 56));
    assertEquals(-1, samples.sampleIndex(bounds, 41, 56), "Space between cells is not clickable");
    assertEquals(-1, samples.sampleIndex(bounds, 75, 56), "Unused final-row cell is not clickable");
    assertEquals(-1, samples.sampleIndex(bounds, 10.9, 24));
    assertEquals(-1, samples.sampleIndex(bounds, 44, 81));
    assertEquals(4, samples.sampleIndex(bounds, 68.9, 80.9));
    var narrow = new Rect(0, 0, 10, 58);
    assertEquals(-1, samples.sampleIndex(narrow, 12, 3), "Clips oversized cells to the control");
  }

  @Test
  void defaultSampleGridKeepsEighteenPixelCells() {
    var samples = new FilterControl.Samples("samples", 5, ResourceType.ITEMS);
    assertArrayEquals(new int[] {40, 54}, samples.measure(TextMeasure.NONE, 40));
    var bounds = new Rect(0, 0, 40, 54);
    assertEquals(4, samples.sampleIndex(bounds, 1, 37));
    assertEquals(-1, samples.sampleIndex(bounds, 19, 37));
    assertEquals(-1, samples.sampleIndex(bounds, 37, 1));
    assertTrue(samples.sampleSlot(0).ghost());
  }

  @Test
  void builtinControlsRejectInvalidValuesAndSamplesUseServerDerivation() {
    var root =
        new FilterControl.Box(
            "root",
            false,
            List.of(
                new FilterControl.Text("text", 3, Component.empty()),
                new FilterControl.Number("number", 1, 4, 1, Component.empty()),
                new FilterControl.Samples("items", 2, ResourceType.ITEMS),
                new FilterControl.Samples("fluid", 2, ResourceType.FLUIDS)));
    var filter = new Filter(root);
    assertFalse(filter.edit("text", 0, "long"));
    assertFalse(filter.edit("text", 0, "\n"));
    assertFalse(filter.edit("number", 0, "99999999999999"));
    assertFalse(filter.edit("number", 0, "0"));
    assertFalse(filter.edit("items", 2, "sample"));
    assertFalse(filter.edit("items", 0, "minecraft:diamond"));
    assertFalse(filter.edit("fluid", 0, "sample"));
    assertTrue(filter.saved.isEmpty());
    assertTrue(filter.edit("text", 0, "abc"));
    assertTrue(filter.edit("number", 0, "3"));
    assertTrue(filter.edit("items", 1, "sample"));
    assertEquals("minecraft:stone", filter.saved.getList("items", 8).getString(1));
    assertTrue(filter.edit("items", 1, "clear"));
    assertEquals("", filter.saved.getList("items", 8).getString(1));
  }
}
