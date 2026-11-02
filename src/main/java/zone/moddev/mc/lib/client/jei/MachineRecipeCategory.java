package zone.moddev.mc.lib.client.jei;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;

import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.recipe.MachineProduct;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/**
 * The JEI category MMDLib registers for every {@link MachineRecipeType}: item and fluid inputs, an
 * arrow, the products, then the energy and the time. It is sized for the largest recipe loaded. Its
 * title is the translation {@code jei.<namespace>.<path>} of the type's ID. A mod adds its machines
 * as catalysts from its own JEI plugin, with {@link #recipeType}. Recipes that can never run, such
 * as one whose input is an empty tag from an optional mod, are left out.
 */
public final class MachineRecipeCategory
    extends AbstractRecipeCategory<RecipeHolder<MachineRecipeEntry>> {
  private static final int CELL = 18, COLUMNS = 3, ARROW = 24, GAP = 4, TEXT = 10;
  private static final int TEXT_COLOR = 0xFF808080;

  private final int inputColumns, rows;

  private MachineRecipeCategory(
      MachineRecipeType type, int inputColumns, int outputColumns, int rows) {
    super(
        recipeType(type),
        Component.translatableWithFallback(
            "jei." + type.id().getNamespace() + "." + type.id().getPath(), type.id().toString()),
        null,
        inputColumns * CELL + GAP + ARROW + GAP + outputColumns * CELL,
        rows * CELL + TEXT);
    this.inputColumns = inputColumns;
    this.rows = rows;
  }

  /** The JEI recipe type of {@code type}, for catalysts and recipe lookups. */
  public static RecipeType<RecipeHolder<MachineRecipeEntry>> recipeType(MachineRecipeType type) {
    return RecipeType.createRecipeHolderType(type.id());
  }

  /** The recipes to show: those whose every input names at least one item or fluid. */
  static List<RecipeHolder<MachineRecipeEntry>> shown(
      Collection<RecipeHolder<MachineRecipeEntry>> recipes) {
    return recipes.stream()
        .filter(
            holder ->
                holder.value().recipe().inputs().values().stream()
                    .flatMap(List::stream)
                    .allMatch(
                        part ->
                            switch (part) {
                              case MachineIngredient.ItemPart items -> !items.items().hasNoItems();
                              case MachineIngredient.FluidPart fluids -> fluids.fluids().size() > 0;
                              case MachineIngredient.EnergyPart energy -> true;
                            }))
        .toList();
  }

  /** A category for {@code type}, wide and tall enough for every recipe in {@code recipes}. */
  static MachineRecipeCategory create(
      MachineRecipeType type, Collection<RecipeHolder<MachineRecipeEntry>> recipes) {
    int inputs = 1, outputs = 1;
    for (var holder : recipes) {
      inputs = Math.max(inputs, inputSlots(holder.value().recipe()));
      outputs = Math.max(outputs, outputSlots(holder.value().recipe()));
    }
    int inputColumns = Math.min(COLUMNS, inputs), outputColumns = Math.min(COLUMNS, outputs);
    int rows = Math.max(ceil(inputs, inputColumns), ceil(outputs, outputColumns));
    return new MachineRecipeCategory(type, inputColumns, outputColumns, rows);
  }

  @Override
  public void setRecipe(
      IRecipeLayoutBuilder builder, RecipeHolder<MachineRecipeEntry> holder, IFocusGroup focuses) {
    var recipe = holder.value().recipe();
    int index = 0;
    for (var parts : recipe.inputs().values())
      for (var part : parts) {
        switch (part) {
          case MachineIngredient.ItemPart items ->
              slot(builder, RecipeIngredientRole.INPUT, 0, index++)
                  .addItemStacks(
                      Arrays.stream(items.items().getItems())
                          .map(stack -> stack.copyWithCount(items.count()))
                          .toList());
          case MachineIngredient.FluidPart fluids -> {
            var slot = slot(builder, RecipeIngredientRole.INPUT, 0, index++);
            for (var fluid : sources(fluids.fluids()))
              slot.addFluidStack(fluid.value(), fluids.millibuckets());
            slot.setFluidRenderer(fluids.millibuckets(), false, 16, 16);
          }
          case MachineIngredient.EnergyPart energy -> {}
        }
      }
    int outputX = inputColumns * CELL + GAP + ARROW + GAP;
    index = 0;
    for (var products : recipe.outputs().values())
      for (var product : products) {
        var slot = slot(builder, RecipeIngredientRole.OUTPUT, outputX, index++);
        switch (product) {
          case MachineProduct.ItemProduct item -> slot.addItemStack(item.stack());
          case MachineProduct.FluidProduct fluid -> {
            slot.addFluidStack(
                fluid.fluid().fluid().value(), fluid.fluid().amount(), fluid.fluid().components());
            slot.setFluidRenderer(fluid.fluid().amount(), false, 16, 16);
          }
        }
        if (product.chance() < 1)
          slot.addRichTooltipCallback(
              (view, tooltip) ->
                  tooltip.add(
                      Component.translatable(
                          "jei.mmdlib.machine.chance", percent(product.chance()))));
      }
  }

  @Override
  public void createRecipeExtras(
      IRecipeExtrasBuilder builder, RecipeHolder<MachineRecipeEntry> holder, IFocusGroup focuses) {
    var recipe = holder.value().recipe();
    builder.addRecipeArrowWidget().setPosition(inputColumns * CELL + GAP, (rows * CELL - 17) / 2);
    var lines = new ArrayList<Component>();
    long energy =
        recipe.inputs().values().stream()
            .flatMap(List::stream)
            .filter(MachineIngredient.EnergyPart.class::isInstance)
            .mapToLong(MachineIngredient::amount)
            .sum();
    if (energy > 0)
      lines.add(
          Component.translatable(
              "jei.mmdlib.machine.energy",
              NumberFormat.getIntegerInstance(Locale.ROOT).format(energy)));
    lines.add(Component.translatable("jei.mmdlib.machine.time", seconds(recipe.time())));
    builder
        .addText(joined(lines), getWidth(), TEXT)
        .setPosition(0, rows * CELL + 1)
        .setColor(TEXT_COLOR);
  }

  private IRecipeSlotBuilder slot(
      IRecipeLayoutBuilder builder, RecipeIngredientRole role, int originX, int index) {
    return builder
        .addSlot(role, originX + index % COLUMNS * CELL + 1, index / COLUMNS * CELL + 1)
        .setStandardSlotBackground();
  }

  /** The source fluids of {@code fluids}, as a tag also lists flowing ones; all if none are. */
  private static List<Holder<Fluid>> sources(HolderSet<Fluid> fluids) {
    var sources =
        fluids.stream().filter(fluid -> fluid.value().isSource(fluid.value().defaultFluidState()));
    var list = sources.toList();
    return list.isEmpty() ? fluids.stream().toList() : list;
  }

  private static int inputSlots(MachineRecipe recipe) {
    return (int)
        recipe.inputs().values().stream()
            .flatMap(List::stream)
            .filter(part -> !(part instanceof MachineIngredient.EnergyPart))
            .count();
  }

  private static int outputSlots(MachineRecipe recipe) {
    return recipe.outputs().values().stream().mapToInt(List::size).sum();
  }

  private static int ceil(int count, int columns) {
    return Math.max(1, (count + columns - 1) / columns);
  }

  private static String percent(float chance) {
    var format = NumberFormat.getNumberInstance(Locale.ROOT);
    format.setMaximumFractionDigits(1);
    return format.format(chance * 100);
  }

  private static String seconds(int ticks) {
    var format = NumberFormat.getNumberInstance(Locale.ROOT);
    format.setMaximumFractionDigits(2);
    return format.format(ticks / 20.0);
  }

  private static Component joined(List<Component> parts) {
    var result = Component.empty();
    for (int i = 0; i < parts.size(); i++) {
      if (i > 0) result.append(" · ");
      result.append(parts.get(i));
    }
    return result;
  }
}
