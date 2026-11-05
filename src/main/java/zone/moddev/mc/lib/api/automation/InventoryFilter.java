package zone.moddev.mc.lib.api.automation;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** A filter's predicate and editor are item-provided, and have no dependency on a client screen. */
public interface InventoryFilter {
  ResourceType<?> resource(ItemStack stack);

  ResourceLocation editorType(ItemStack stack);

  FilterControl controls(ItemStack stack);

  boolean matches(ItemStack filter, Object resource, HolderLookup.Provider registries);

  default CompoundTag configuration(ItemStack stack) {
    return stack
        .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
        .copyTag()
        .getCompound("mmdlib_filter");
  }

  default void configure(ItemStack stack, CompoundTag configuration) {
    var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    data.put("mmdlib_filter", configuration.copy());
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
  }

  /** Derive an approved sample from the authoritative cursor; never trust client item NBT. */
  default String sample(ItemStack filter, ItemStack cursor, HolderLookup.Provider registries) {
    if (cursor.isEmpty()) return "";
    if (resource(filter) == ResourceType.ITEMS)
      return net.minecraft.core.registries.BuiltInRegistries.ITEM
          .getKey(cursor.getItem())
          .toString();
    if (resource(filter) == ResourceType.FLUIDS) {
      var fluid = net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(cursor);
      return fluid
          .map(
              f ->
                  net.minecraft.core.registries.BuiltInRegistries.FLUID
                      .getKey(f.getFluid())
                      .toString())
          .orElse("");
    }
    return "";
  }

  /** Validate the whole candidate before the host atomically replaces its installed stack. */
  default boolean validate(
      ItemStack filter, CompoundTag candidate, HolderLookup.Provider registries) {
    return true;
  }

  /** Standard controls; custom filters can further constrain the proposed configuration. */
  default boolean edit(
      ItemStack copy,
      String id,
      int index,
      String value,
      ItemStack cursor,
      HolderLookup.Provider registries) {
    var control =
        FilterControl.flatten(controls(copy)).stream()
            .filter(c -> c.id().equals(id))
            .findFirst()
            .orElse(null);
    var config = configuration(copy);
    switch (control) {
      case FilterControl.Custom custom -> {
        if (!custom.type().dispatch(config, value)) return false;
      }
      case FilterControl.Toggle toggle -> config.putBoolean(id, !config.getBoolean(id));
      case FilterControl.Text text -> {
        if (value.length() > text.maxLength()
            || value.codePoints().anyMatch(Character::isISOControl)) return false;
        config.putString(id, value);
      }
      case FilterControl.Number number -> {
        int proposed;
        try {
          proposed = Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
          return false;
        }
        if (proposed < number.min() || proposed > number.max()) return false;
        config.putInt(id, proposed);
      }
      case FilterControl.Samples samples -> {
        if (!value.equals("sample") && !value.equals("clear")) return false;
        if (index < 0 || index >= samples.count() || samples.resource() != resource(copy))
          return false;
        var entries = config.getList(id, Tag.TAG_STRING).copy();
        while (entries.size() < samples.count()) entries.add(StringTag.valueOf(""));
        String sample = value.equals("clear") ? "" : sample(copy, cursor, registries);
        if (!value.equals("clear") && sample.isEmpty()) return false;
        entries.set(index, StringTag.valueOf(sample));
        config.put(id, entries);
      }
      case null, default -> {
        return false;
      }
    }
    if (!validate(copy, config, registries)) return false;
    configure(copy, config);
    return true;
  }
}
