package zone.moddev.mc.lib.machine.recipe.generation;

import java.util.*;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/** Snapshot of staged item/block tags, never stale globally bound tags during reload. */
public record ReloadTags(
    Map<ResourceLocation, List<Holder<Item>>> items,
    Map<ResourceLocation, List<Holder<Block>>> blocks) {
  public ReloadTags {
    items = snapshot(items);
    blocks = snapshot(blocks);
  }

  public ReloadTags(Map<ResourceLocation, List<Holder<Item>>> items) {
    this(items, Map.of());
  }

  private static <T> Map<ResourceLocation, List<Holder<T>>> snapshot(
      Map<ResourceLocation, List<Holder<T>>> tags) {
    var copy = new TreeMap<ResourceLocation, List<Holder<T>>>();
    tags.forEach((id, values) -> copy.put(id, List.copyOf(values)));
    return Collections.unmodifiableMap(copy);
  }

  public boolean contains(TagKey<Block> tag, Block block) {
    return blocks.getOrDefault(tag.location(), List.of()).stream()
        .anyMatch(holder -> holder.value() == block);
  }

  public List<Holder<Item>> members(ResourceLocation id) {
    return items.getOrDefault(id, List.of());
  }

  public Ingredient ingredient(ResourceLocation id) {
    return Ingredient.of(members(id).stream().map(h -> new ItemStack(h.value())));
  }
}
