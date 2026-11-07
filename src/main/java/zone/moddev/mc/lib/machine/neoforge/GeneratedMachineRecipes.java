package zone.moddev.mc.lib.machine.neoforge;

import java.lang.reflect.Constructor;
import java.util.*;

import com.mojang.serialization.JsonOps;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;

import net.neoforged.neoforge.event.AddReloadListenerEvent;

import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.recipe.*;
import zone.moddev.mc.lib.machine.recipe.generation.*;

/**
 * Appended after vanilla tag/recipe listeners: publishes only into the candidate reload manager.
 */
public final class GeneratedMachineRecipes {
  private record Provider(
      MachineDefinition.Generator definition,
      Constructor<? extends MachineRecipeGenerator> constructor) {}

  private static final Map<ResourceLocation, Provider> providers = new TreeMap<>();

  private GeneratedMachineRecipes() {}

  static void configure(Map<ResourceLocation, MachineDefinition.RecipeType> declarations) {
    providers.clear();
    declarations.forEach(
        (id, type) -> {
          var generator = type.generator();
          if (generator == null) return;
          try {
            var clazz =
                Class.forName(generator.className(), false, generator.loader())
                    .asSubclass(MachineRecipeGenerator.class);
            if (!java.lang.reflect.Modifier.isPublic(clazz.getModifiers())
                || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers()))
              throw new IllegalArgumentException(
                  "Generator class must be public and concrete: " + generator.className());
            providers.put(id, new Provider(generator, clazz.getConstructor()));
          } catch (ReflectiveOperationException | ClassCastException ex) {
            throw new IllegalArgumentException(
                generator.source()
                    + ":"
                    + generator.line()
                    + ": invalid recipe generator "
                    + generator.className(),
                ex);
          }
        });
  }

  public static void register(AddReloadListenerEvent event) {
    event.addListener(
        new SimplePreparableReloadListener<Boolean>() {
          @Override
          protected Boolean prepare(ResourceManager manager, ProfilerFiller profiler) {
            return true;
          }

          @Override
          protected void apply(Boolean prepared, ResourceManager manager, ProfilerFiller profiler) {
            var recipes = event.getServerResources().getRecipeManager();
            var tags = new TreeMap<ResourceLocation, List<Holder<Item>>>();
            event
                .getConditionContext()
                .getAllTags(Registries.ITEM)
                .forEach((id, holders) -> tags.put(id, List.copyOf(holders)));
            var blockTags =
                new TreeMap<
                    ResourceLocation, List<Holder<net.minecraft.world.level.block.Block>>>();
            event
                .getConditionContext()
                .getAllTags(Registries.BLOCK)
                .forEach((id, holders) -> blockTags.put(id, List.copyOf(holders)));
            recipes.replaceRecipes(
                generate(
                    List.copyOf(recipes.getRecipes()),
                    event.getServerResources().getRegistryLookup(),
                    new ReloadTags(tags, blockTags)));
          }
        });
  }

  /**
   * Builds a fresh candidate; exposed to runtime tests without altering the active recipe manager.
   */
  public static List<RecipeHolder<?>> generate(
      List<RecipeHolder<?>> authored,
      net.minecraft.core.HolderLookup.Provider registries,
      ReloadTags tags) {
    var expanded = expand(authored);
    var generated = new TreeMap<ResourceLocation, RecipeHolder<?>>();
    var original = new TreeMap<ResourceLocation, RecipeHolder<?>>();
    expanded.forEach(holder -> original.put(holder.id(), holder));
    providers.forEach(
        (id, provider) -> {
          var spec = provider.definition();
          var properties = new GeneratorProperties(spec.properties(), spec.source(), spec.line());
          var type = MachineContent.recipeType(id);
          try {
            provider
                .constructor()
                .newInstance()
                .generate(
                    new RecipeGenerationContext(type, registries, tags, expanded, properties),
                    (recipeId, recipe) -> {
                      if (!recipeId.getNamespace().equals(id.getNamespace()))
                        throw properties.error(
                            "Generated recipe must use namespace " + id.getNamespace());
                      // Round-trip validates every amount/product and takes an independent
                      // snapshot.
                      var ops = registries.createSerializationContext(JsonOps.INSTANCE);
                      var encoded =
                          MachineRecipe.CODEC.codec().encodeStart(ops, recipe).getOrThrow();
                      var snapshot = MachineRecipe.CODEC.codec().parse(ops, encoded).getOrThrow();
                      validateRecipe(
                          new RecipeHolder<>(recipeId, new MachineRecipeEntry(type, snapshot)));
                      if (generated.putIfAbsent(
                              recipeId,
                              new RecipeHolder<>(recipeId, new MachineRecipeEntry(type, snapshot)))
                          != null) throw properties.error("Duplicate generated recipe " + recipeId);
                    });
          } catch (ReflectiveOperationException | RuntimeException ex) {
            throw new IllegalArgumentException(
                spec.source()
                    + ":"
                    + spec.line()
                    + ": generator "
                    + spec.className()
                    + " failed: "
                    + ex.getMessage(),
                ex);
          }
        });
    generated.putAll(original); // Authored files replace generated entries with the same ID.
    generated.values().forEach(GeneratedMachineRecipes::validateRecipe);
    return List.copyOf(generated.values());
  }

  private static List<RecipeHolder<?>> expand(List<RecipeHolder<?>> authored) {
    var result = new TreeMap<ResourceLocation, RecipeHolder<?>>();
    for (var holder : authored) {
      if (holder.value() instanceof MachineRecipeBundle bundle) {
        bundle
            .recipes()
            .forEach(
                (key, conditional) ->
                    conditional.ifPresent(
                        value -> {
                          var id =
                              ResourceLocation.fromNamespaceAndPath(
                                  holder.id().getNamespace(), holder.id().getPath() + "/" + key);
                          if (!(value.carrier() instanceof MachineRecipeEntry))
                            throw new IllegalArgumentException(
                                "Recipe "
                                    + id
                                    + " in "
                                    + holder.id()
                                    + ": bundle entries must be machine recipes");
                          add(result, new RecipeHolder<>(id, value.carrier()));
                        }));
      } else add(result, holder);
    }
    return List.copyOf(result.values());
  }

  private static void add(Map<ResourceLocation, RecipeHolder<?>> result, RecipeHolder<?> holder) {
    if (result.putIfAbsent(holder.id(), holder) != null)
      throw new IllegalArgumentException(
          "Duplicate authored recipe " + holder.id() + " (bundle entry or standalone file)");
  }

  private static void validateRecipe(RecipeHolder<?> holder) {
    if (!(holder.value() instanceof MachineRecipeEntry entry)) return;
    try {
      validateGroups(entry.type(), entry.recipe());
    } catch (IllegalArgumentException error) {
      throw new IllegalArgumentException(
          "Recipe " + holder.id() + ": " + error.getMessage(), error);
    }
  }

  private static void validateGroups(MachineRecipeType type, MachineRecipe recipe) {
    for (var machine : MachineContent.definitions().values())
      if (machine.recipeType().id().equals(type.id())) {
        var items = new HashSet<String>();
        machine.groups().forEach(g -> items.add(g.id()));
        recipe
            .inputs()
            .forEach(
                (key, parts) -> {
                  if (!items.contains(key) && !key.equals(machine.power().id()))
                    throw new IllegalArgumentException(
                        machine.id() + ": unknown recipe input group " + key);
                  parts.forEach(
                      part -> {
                        boolean valid =
                            part instanceof MachineIngredient.ItemPart
                                ? items.contains(key)
                                : part instanceof MachineIngredient.EnergyPart
                                    && key.equals(machine.power().id());
                        if (!valid)
                          throw new IllegalArgumentException(
                              machine.id() + ": incompatible recipe input group " + key);
                      });
                });
        recipe
            .outputs()
            .forEach(
                (key, parts) -> {
                  if (!items.contains(key))
                    throw new IllegalArgumentException(
                        machine.id() + ": unknown recipe output group " + key);
                  parts.forEach(
                      part -> {
                        if (!(part instanceof MachineProduct.ItemProduct) || !items.contains(key))
                          throw new IllegalArgumentException(
                              machine.id() + ": incompatible recipe output group " + key);
                      });
                });
      }
  }
}
