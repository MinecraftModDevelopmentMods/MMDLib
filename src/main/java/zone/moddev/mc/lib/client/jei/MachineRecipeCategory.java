package zone.moddev.mc.lib.client.jei;

import java.text.NumberFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;

import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;
import zone.moddev.mc.lib.machine.recipe.MachineProduct;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/**
 * The JEI category MMDLib registers for every {@link MachineRecipeType}: item
 * and fluid inputs, an arrow, the products, then the energy and the time. It is
 * sized for the largest recipe loaded. Its title is the translation
 * {@code jei.<namespace>.<path>} of the type's ID. A mod adds its machines as
 * catalysts from its own JEI plugin, with {@link #recipeType}. Recipes that can
 * never run, such as one whose input is an empty tag from an optional mod, are
 * left out.
 */
public final class MachineRecipeCategory
    extends
        AbstractRecipeCategory<RecipeHolder<MachineRecipeEntry>> {
    private static final int CELL = MachineRecipeLayout.CELL, TEXT = 10;
    private static final int TEXT_COLOR = 0xFF808080;

    private final int rows;
    private final MachineRecipeLayout layout;
    private final Set<String> ghostInputs;
    private final IIngredientManager ingredients;
    private final IIngredientRenderer<ItemStack> ghostItems;

    private MachineRecipeCategory(MachineRecipeType type, int width,
        MachineRecipeLayout layout, Set<String> ghostInputs,
        IJeiHelpers helpers) {
        super(recipeType(type),
            Component.translatableWithFallback(
                "jei." + type.id().getNamespace() + "." + type.id().getPath(),
                type.id().toString()),
            null, width, layout.rows() * CELL + TEXT);
        this.layout = layout;
        this.rows = layout.rows();
        this.ghostInputs = ghostInputs;
        ingredients = helpers.getIngredientManager();
        ghostItems = JeiGhostRenderer
            .items(ingredients.getIngredientRenderer(VanillaTypes.ITEM_STACK));
    }

    /**
     * The JEI recipe type of {@code type}, for catalysts and recipe lookups.
     */
    public static RecipeType<RecipeHolder<MachineRecipeEntry>> recipeType(
        MachineRecipeType type) {
        return RecipeType.createRecipeHolderType(type.id());
    }

    /**
     * The recipes to show: those whose every input names at least one item or
     * fluid.
     */
    static List<RecipeHolder<MachineRecipeEntry>> shown(
        Collection<RecipeHolder<MachineRecipeEntry>> recipes) {
        return recipes.stream()
            .filter(holder -> holder.value().recipe().inputs().values().stream()
                .flatMap(List::stream).allMatch(part -> switch (part) {
                    case MachineIngredient.ItemPart items ->
                        !items.items().hasNoItems();
                    case MachineIngredient.FluidPart fluids ->
                        fluids.fluids().size() > 0;
                    case MachineIngredient.EnergyPart energy -> true;
                }))
            .toList();
    }

    /**
     * A category for {@code type}, wide and tall enough for every recipe in
     * {@code recipes}.
     */
    static MachineRecipeCategory create(MachineRecipeType type,
        Collection<RecipeHolder<MachineRecipeEntry>> recipes,
        IJeiHelpers helpers) {
        var ghosts = MachineDefinition
            .ghostInputsFor(MachineContent.definitions().values(), type.id());
        int width = 1;
        var layout = new MachineRecipeLayout(0, 0, 0);
        for (var holder : recipes) {
            var recipe = holder.value().recipe();
            layout = layout.merge(layout(recipe, ghosts));
            width = Math.max(width,
                Minecraft.getInstance().font.width(details(recipe)));
        }
        return new MachineRecipeCategory(type, Math.max(width, layout.width()),
            layout, ghosts, helpers);
    }

    private static boolean ghost(String group, MachineIngredient part,
        Set<String> ghostInputs) {
        return !part.consumed() || part instanceof MachineIngredient.ItemPart
            && ghostInputs.contains(group);
    }

    private static MachineRecipeLayout layout(MachineRecipe recipe,
        Set<String> ghostInputs) {
        int ghosts = 0, inputs = 0;
        for (var entry : recipe.inputs().entrySet())
            for (var part : entry.getValue()) {
                if (part instanceof MachineIngredient.EnergyPart)
                    continue;
                if (ghost(entry.getKey(), part, ghostInputs))
                    ghosts++;
                else
                    inputs++;
            }
        return new MachineRecipeLayout(ghosts, inputs, outputSlots(recipe));
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
        RecipeHolder<MachineRecipeEntry> holder, IFocusGroup focuses) {
        var recipe = holder.value().recipe();
        var recipeLayout = layout(recipe, ghostInputs);
        int originX = (getWidth() - layout.width()) / 2;
        int ghostIndex = 0, inputIndex = 0;
        for (var entry : recipe.inputs().entrySet())
            for (var part : entry.getValue()) {
                if (part instanceof MachineIngredient.EnergyPart)
                    continue;
                boolean ghost = ghost(entry.getKey(), part, ghostInputs);
                boolean sample = part instanceof MachineIngredient.ItemPart
                    && ghostInputs.contains(entry.getKey());
                var role = sample
                    ? RecipeIngredientRole.RENDER_ONLY
                    : ghost
                        ? RecipeIngredientRole.CATALYST
                        : RecipeIngredientRole.INPUT;
                var slot = slot(builder, role,
                    originX + (ghost ? 0 : layout.inputX()),
                    ghost ? ghostIndex++ : inputIndex++,
                    ghost ? recipeLayout.ghosts() : recipeLayout.inputs());
                slot.setSlotName(
                    (sample ? "sample/" : ghost ? "catalyst/" : "input/")
                        + entry.getKey() + "/"
                        + (ghost ? ghostIndex : inputIndex));
                switch (part) {
                    case MachineIngredient.ItemPart items -> {
                        slot.addItemStacks(Arrays
                            .stream(items.items().getItems())
                            .map(stack -> stack.copyWithCount(items.count()))
                            .toList());
                        if (ghost)
                            slot.setCustomRenderer(VanillaTypes.ITEM_STACK,
                                ghostItems);
                    }
                    case MachineIngredient.FluidPart fluids -> {
                        for (var fluid : sources(fluids.fluids()))
                            slot.addFluidStack(fluid.value(),
                                fluids.millibuckets());
                        long capacity = MachineRecipeLayout
                            .fluidCapacity(fluids.millibuckets());
                        if (ghost) {
                            slot.setCustomRenderer(NeoForgeTypes.FLUID_STACK,
                                JeiGhostRenderer
                                    .fluids(
                                        ingredients.getIngredientRenderer(
                                            NeoForgeTypes.FLUID_STACK),
                                        capacity));
                            // JEI's default ingredient-list renderer does not
                            // promise an amount
                            // tooltip. A ghost still represents the full recipe
                            // requirement.
                            slot.addRichTooltipCallback((view,
                                tooltip) -> tooltip.add(Component.translatable(
                                    "jei.mmdlib.machine.required_fluid",
                                    NumberFormat.getIntegerInstance(Locale.ROOT)
                                        .format(fluids.millibuckets()))));
                        } else
                            slot.setFluidRenderer(capacity, false, 16, 16);
                    }
                    case MachineIngredient.EnergyPart energy -> {
                    }
                }
                if (ghost)
                    slot.addRichTooltipCallback(
                        (view, tooltip) -> tooltip.add(Component
                            .translatable("jei.mmdlib.machine.not_consumed")));
            }
        int outputX = originX + layout.outputX();
        int index = 0;
        for (var products : recipe.outputs().values())
            for (var product : products) {
                var slot = slot(builder, RecipeIngredientRole.OUTPUT, outputX,
                    index++, recipeLayout.outputs());
                switch (product) {
                    case MachineProduct.ItemProduct item ->
                        slot.addItemStack(item.stack());
                    case MachineProduct.FluidProduct fluid -> {
                        slot.addFluidStack(fluid.fluid().fluid().value(),
                            fluid.fluid().amount(), fluid.fluid().components());
                        slot.setFluidRenderer(MachineRecipeLayout.fluidCapacity(
                            fluid.fluid().amount()), false, 16, 16);
                    }
                }
                if (product.chance() < 1)
                    slot.addRichTooltipCallback((view, tooltip) -> tooltip
                        .add(Component.translatable("jei.mmdlib.machine.chance",
                            percent(product.chance()))));
            }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder,
        RecipeHolder<MachineRecipeEntry> holder, IFocusGroup focuses) {
        var recipe = holder.value().recipe();
        int originX = (getWidth() - layout.width()) / 2;
        if (layout(recipe, ghostInputs).separator())
            builder.addText(Component.literal(":"), 4, TEXT)
                .setPosition(originX
                    + MachineRecipeLayout.columns(layout.ghosts()) * CELL + 4,
                    (rows * CELL - TEXT) / 2)
                .setColor(TEXT_COLOR);
        builder.addRecipeArrowWidget().setPosition(originX + layout.arrowX(),
            (rows * CELL - 17) / 2);
        builder.addText(details(recipe), getWidth(), TEXT)
            .setPosition(0, rows * CELL + 1).setColor(TEXT_COLOR);
    }

    private static Component details(MachineRecipe recipe) {
        var lines = new ArrayList<Component>();
        long energy = recipe.inputs().values().stream().flatMap(List::stream)
            .filter(MachineIngredient.EnergyPart.class::isInstance)
            .mapToLong(MachineIngredient::amount).sum();
        if (energy > 0)
            lines.add(Component.translatable("jei.mmdlib.machine.energy",
                NumberFormat.getIntegerInstance(Locale.ROOT).format(energy)));
        lines.add(Component.translatable("jei.mmdlib.machine.time",
            seconds(recipe.time())));
        return joined(lines);
    }

    private IRecipeSlotBuilder slot(IRecipeLayoutBuilder builder,
        RecipeIngredientRole role, int originX, int index, int count) {
        int columns = MachineRecipeLayout.columns(count);
        int top = (rows - MachineRecipeLayout.rows(count)) * CELL / 2;
        return builder.addSlot(role, originX + index % columns * CELL + 1,
            top + index / columns * CELL + 1).setStandardSlotBackground();
    }

    /**
     * The source fluids of {@code fluids}, as a tag also lists flowing ones;
     * all if none are.
     */
    private static List<Holder<Fluid>> sources(HolderSet<Fluid> fluids) {
        var sources = fluids.stream().filter(
            fluid -> fluid.value().isSource(fluid.value().defaultFluidState()));
        var list = sources.toList();
        return list.isEmpty() ? fluids.stream().toList() : list;
    }

    private static int outputSlots(MachineRecipe recipe) {
        return recipe.outputs().values().stream().mapToInt(List::size).sum();
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
            if (i > 0)
                result.append(" · ");
            result.append(parts.get(i));
        }
        return result;
    }
}
