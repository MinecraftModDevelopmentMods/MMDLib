package zone.moddev.mc.lib.machine.recipe.generation;

import java.util.*;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import zone.moddev.mc.lib.machine.recipe.MachineRecipeBuilder;

/** Reusable ore processing: joins nonempty c:ores/material and c:ingots/material item tags. */
public final class OreToIngotGenerator implements MachineRecipeGenerator {
  public static final int INPUT_COUNT = 1;
  public static final int OUTPUT_COUNT = 2;
  public static final int DEFAULT_TIME = 100;
  public static final long DEFAULT_ENERGY = 2000;
  public static final String DEFAULT_PREFERRED_MODS = "minecraft;mmdlib";

  public OreToIngotGenerator() {}

  @Override
  public void generate(RecipeGenerationContext ctx, MachineRecipeOutput output) {
    var p = ctx.properties();
    p.requireOnly(
        "input-group",
        "output-group",
        "energy-group",
        "time",
        "energy",
        "preferred-mods",
        "tool-level-ratio",
        "hardness-level-ratio");
    String input = p.string("input-group", "input"),
        result = p.string("output-group", "output"),
        power = p.string("energy-group", "energy");
    int ticks = p.integer("time", DEFAULT_TIME, 1, Integer.MAX_VALUE);
    long energy = p.longValue("energy", DEFAULT_ENERGY, 1, Long.MAX_VALUE);
    double toolRatio = p.decimal("tool-level-ratio", 0.0, 0.0, Double.MAX_VALUE);
    double hardnessRatio = p.decimal("hardness-level-ratio", 0.0, 0.0, Double.MAX_VALUE);
    var preferred = preferredMods(p);
    Comparator<Holder<Item>> order =
        Comparator.comparingInt(
                (Holder<Item> h) ->
                    preferred.getOrDefault(
                        BuiltInRegistries.ITEM.getKey(h.value()).getNamespace(), Integer.MAX_VALUE))
            .thenComparing(h -> BuiltInRegistries.ITEM.getKey(h.value()));
    for (var tag : ctx.tags().items().keySet()) {
      if (!tag.getNamespace().equals("c")
          || !tag.getPath().startsWith("ores/")
          || ctx.tags().members(tag).isEmpty()) continue;
      String metal = tag.getPath().substring("ores/".length());
      if (metal.isEmpty()) continue;
      var ingots =
          ctx.tags().members(ResourceLocation.fromNamespaceAndPath("c", "ingots/" + metal));
      if (ingots.isEmpty()) continue;
      var stack = new ItemStack(ingots.stream().min(order).orElseThrow().value(), OUTPUT_COUNT);
      if (OUTPUT_COUNT > stack.getMaxStackSize())
        throw p.error("Output count exceeds stack limit for " + metal);
      var id =
          ResourceLocation.fromNamespaceAndPath(
              ctx.recipeType().id().getNamespace(),
              "generated/" + ctx.recipeType().id().getPath() + "/ores/" + metal);
      if (toolRatio == 0 && hardnessRatio == 0) {
        emit(
            ctx,
            output,
            id,
            input,
            result,
            power,
            ctx.tags().ingredient(tag),
            stack,
            ticks,
            energy);
        continue;
      }
      // Each ore variant keeps its own cost and stable ID when block scaling is enabled.
      for (var ore :
          ctx.tags().members(tag).stream()
              .sorted(Comparator.comparing(h -> BuiltInRegistries.ITEM.getKey(h.value())))
              .toList()) {
        var item = ore.value();
        double hardness = 0;
        int level = 0;
        if (item instanceof BlockItem blockItem) {
          var block = blockItem.getBlock();
          hardness = block.defaultDestroyTime();
          if (hardness < 0) continue; // Unbreakable blocks cannot be processed by mining cost.
          if (!Double.isFinite(hardness))
            throw p.error("Invalid block hardness: " + BuiltInRegistries.ITEM.getKey(item));
          level = toolLevel(ctx.tags(), block);
        }
        double factor = 1.0 + level * toolRatio + hardness * hardnessRatio;
        var oreId = BuiltInRegistries.ITEM.getKey(item);
        var recipeId =
            ResourceLocation.fromNamespaceAndPath(
                id.getNamespace(),
                id.getPath() + "/" + oreId.getNamespace() + "/" + oreId.getPath());
        emit(
            ctx,
            output,
            recipeId,
            input,
            result,
            power,
            Ingredient.of(item),
            stack,
            (int) scaled(ticks, factor, Integer.MAX_VALUE, p, "time"),
            scaled(energy, factor, Long.MAX_VALUE, p, "energy"));
      }
    }
  }

  private static Map<String, Integer> preferredMods(GeneratorProperties p) {
    var result = new LinkedHashMap<String, Integer>();
    for (String part : p.string("preferred-mods", DEFAULT_PREFERRED_MODS).split(";", -1)) {
      String namespace = part.trim();
      if (!namespace.matches("[a-z0-9_.-]+"))
        throw p.error("preferred-mods must be a ';'-separated list of namespaces");
      if (result.putIfAbsent(namespace, result.size()) != null)
        throw p.error("Duplicate preferred mod " + namespace);
    }
    return result;
  }

  private static long scaled(
      long base, double factor, long max, GeneratorProperties p, String name) {
    // Preserve exact unscaled long costs, including Long.MAX_VALUE.
    if (factor == 1.0) return base;
    double value = base * factor;
    if (Double.isFinite(value)) {
      double nearest = Math.rint(value);
      // Ignore a few rounding steps at an integer boundary (100 * 1.1 is just above 110).
      double rounded =
          Math.abs(value - nearest) <= 4 * Math.ulp(value) ? nearest : Math.ceil(value);
      // A double comparison with Long.MAX_VALUE would round that bound up to 2^63.
      if (rounded >= 1 && rounded < 0x1.0p63 && rounded <= max) return (long) rounded;
    }
    throw p.error("Scaled " + name + " exceeds 1.." + max);
  }

  private static int toolLevel(ReloadTags tags, Block block) {
    if (!block.defaultBlockState().requiresCorrectToolForDrops()) return 0;
    if (tags.contains(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, block)) return 5;
    if (tags.contains(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, block)) return 4;
    if (tags.contains(BlockTags.NEEDS_DIAMOND_TOOL, block)
        || tags.contains(BlockTags.INCORRECT_FOR_IRON_TOOL, block)) return 3;
    if (tags.contains(BlockTags.NEEDS_IRON_TOOL, block)
        || tags.contains(BlockTags.INCORRECT_FOR_STONE_TOOL, block)) return 2;
    if (tags.contains(BlockTags.NEEDS_STONE_TOOL, block)
        || tags.contains(BlockTags.INCORRECT_FOR_WOODEN_TOOL, block)) return 1;
    return 0;
  }

  private static void emit(
      RecipeGenerationContext ctx,
      MachineRecipeOutput output,
      ResourceLocation id,
      String input,
      String result,
      String power,
      Ingredient ingredient,
      ItemStack product,
      int ticks,
      long energy) {
    output.accept(
        id,
        MachineRecipeBuilder.recipe(ctx.recipeType())
            .inputItems(input, ingredient, INPUT_COUNT)
            .inputEnergy(power, energy)
            .outputItem(result, product.copy())
            .time(ticks)
            .build());
  }
}
