package zone.moddev.mc.lib.machine.neoforge;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.fluids.FluidStack;

import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.inventory.*;
import zone.moddev.mc.lib.machine.recipe.*;

/**
 * Admission tests depend on recipe ingredients, never on currently available amounts or outputs.
 */
public final class RecipeInputFilters {
  public record Link(MachineRecipeType type, String group) {}

  private final Supplier<Level> level;
  private final MachineInventory inventory;

  public RecipeInputFilters(Supplier<Level> level, MachineInventory inventory) {
    this.level = level;
    this.inventory = inventory;
  }

  public boolean acceptsItem(List<Link> links, ItemStack stack) {
    return inputs(links)
        .anyMatch(
            part -> part instanceof MachineIngredient.ItemPart item && item.items().test(stack));
  }

  public boolean acceptsFluid(List<Link> links, FluidStack stack) {
    return inputs(links)
        .anyMatch(
            part ->
                part instanceof MachineIngredient.FluidPart fluid
                    && fluid.fluids().contains(stack.getFluid().builtInRegistryHolder()));
  }

  private java.util.stream.Stream<MachineIngredient> inputs(List<Link> links) {
    var world = level.get();
    if (world == null) return java.util.stream.Stream.empty();
    // Consult the manager each time: replacements from server reload and client recipe sync apply
    // immediately.
    return links.stream()
        .flatMap(
            link ->
                link.type.recipes(world.getRecipeManager()).stream()
                    .map(holder -> holder.value().recipe())
                    .filter(this::compatible)
                    .flatMap(
                        recipe -> recipe.inputs().getOrDefault(link.group, List.of()).stream()));
  }

  private boolean compatible(MachineRecipe recipe) {
    for (var entry : recipe.inputs().entrySet()) {
      var group = inventory.group(entry.getKey()).orElse(null);
      if (group == null) return false;
      for (var part : entry.getValue()) {
        if (!(part instanceof MachineIngredient.ItemPart && group instanceof ItemGroup)
            && !(part instanceof MachineIngredient.FluidPart && group instanceof FluidGroup)
            && !(part instanceof MachineIngredient.EnergyPart && group instanceof EnergyGroup))
          return false;
      }
    }
    for (var entry : recipe.outputs().entrySet()) {
      var group = inventory.group(entry.getKey()).orElse(null);
      if (group == null) return false;
      for (var product : entry.getValue())
        if (!(product instanceof MachineProduct.ItemProduct && group instanceof ItemGroup)
            && !(product instanceof MachineProduct.FluidProduct && group instanceof FluidGroup))
          return false;
    }
    return true;
  }
}
