package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.blamejared.crafttweaker.api.fluid.CTFluidIngredient;
import com.blamejared.crafttweaker.api.fluid.IFluidStack;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker.api.recipe.component.BuiltinRecipeComponents;
import com.blamejared.crafttweaker.api.recipe.component.IDecomposedRecipe;
import com.blamejared.crafttweaker.api.recipe.handler.IRecipeHandler;
import com.blamejared.crafttweaker.api.recipe.manager.base.IRecipeManager;
import com.blamejared.crafttweaker.api.tag.CraftTweakerTagRegistry;
import com.blamejared.crafttweaker.api.util.StringUtil;
import com.blamejared.crafttweaker.api.util.random.Percentaged;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;

import zone.moddev.mc.lib.compat.CraftTweaker.MachineRecipeComponents.Kind;
import zone.moddev.mc.lib.compat.CraftTweaker.MachineRecipeComponents.Part;
import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.recipe.MachineProduct;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/**
 * CraftTweaker's view of XML machine recipes: {@code /ct recipes} dumps them as
 * {@code MachineRecipeBuilder} calls, and ingredient replacement takes them
 * apart and rebuilds them. Item inputs, fluid inputs, item and fluid outputs
 * and time use CraftTweaker's built-in components; each part's group, count,
 * consumption and chance, and energy, use {@link MachineRecipeComponents}.
 */
final class MachineRecipeHandler implements IRecipeHandler<MachineRecipeEntry> {
    @Override
    public String dumpToCommandString(
        IRecipeManager<? super MachineRecipeEntry> manager,
        RegistryAccess registryAccess,
        RecipeHolder<MachineRecipeEntry> holder) {
        var entry = holder.value();
        var recipe = entry.recipe();
        var out = new StringBuilder("new MachineRecipeBuilder(")
            .append(StringUtil.quoteAndEscape(entry.type().id().toString()))
            .append(")");
        recipe.inputs().forEach((group, parts) -> parts.forEach(part -> {
            var name = StringUtil.quoteAndEscape(group);
            switch (part) {
                case MachineIngredient.ItemPart items -> out
                    .append(".inputItems(").append(name).append(", ")
                    .append(IIngredient.fromIngredient(items.items())
                        .getCommandString())
                    .append(", ").append(items.count())
                    .append(items.consumed() ? "" : ", false").append(")");
                case MachineIngredient.FluidPart fluids ->
                    out.append(".inputFluids(").append(name).append(", ")
                        .append(fluid(fluids).getCommandString())
                        .append(fluids.consumed() ? "" : ", false").append(")");
                case MachineIngredient.EnergyPart energy ->
                    out.append(".inputEnergy(").append(name).append(", ")
                        .append(energy.amount()).append(")");
            }
        }));
        recipe.outputs().forEach((group, parts) -> parts.forEach(part -> {
            var name = StringUtil.quoteAndEscape(group);
            switch (part) {
                case MachineProduct.ItemProduct item ->
                    out.append(".outputItem(").append(name).append(", ")
                        .append(IItemStack.of(item.stack()).getCommandString())
                        .append(", ").append(item.chance()).append(")");
                case MachineProduct.FluidProduct fluid ->
                    out.append(".outputFluid(").append(name).append(", ")
                        .append(stack(fluid.fluid()).getCommandString())
                        .append(", ").append(fluid.chance()).append(")");
            }
        }));
        return out.append(".time(").append(recipe.time()).append(").build(")
            .append(StringUtil.quoteAndEscape(holder.id())).append(");")
            .toString();
    }

    /** Machines pick the first matching recipe; nothing conflicts. */
    @Override
    public <U extends Recipe<?>> boolean doesConflict(
        IRecipeManager<? super MachineRecipeEntry> manager,
        MachineRecipeEntry first, U second) {
        return false;
    }

    @Override
    public Optional<IDecomposedRecipe> decompose(
        IRecipeManager<? super MachineRecipeEntry> manager,
        RegistryAccess registryAccess, MachineRecipeEntry entry) {
        var recipe = entry.recipe();
        var layout = new ArrayList<Part>();
        var items = new ArrayList<IIngredient>();
        var fluids = new ArrayList<CTFluidIngredient>();
        var energy = new ArrayList<Long>();
        var itemOutputs = new ArrayList<Percentaged<IItemStack>>();
        var fluidOutputs = new ArrayList<IFluidStack>();
        recipe.inputs().forEach((group, parts) -> parts.forEach(part -> {
            switch (part) {
                case MachineIngredient.ItemPart i -> {
                    layout.add(new Part(Kind.ITEMS, group, i.count(),
                        i.consumed(), 1));
                    items.add(IIngredient.fromIngredient(i.items()));
                }
                case MachineIngredient.FluidPart f -> {
                    layout.add(new Part(Kind.FLUID, group, f.millibuckets(),
                        f.consumed(), 1));
                    fluids.add(fluid(f));
                }
                case MachineIngredient.EnergyPart e -> {
                    layout.add(new Part(Kind.ENERGY, group, 1, true, 1));
                    energy.add(e.amount());
                }
            }
        }));
        recipe.outputs().forEach((group, parts) -> parts.forEach(part -> {
            switch (part) {
                case MachineProduct.ItemProduct i -> {
                    layout.add(new Part(Kind.ITEM_OUTPUT, group,
                        i.stack().getCount(), true, i.chance()));
                    var stack = IItemStack.of(i.stack());
                    itemOutputs.add(new Percentaged<>(stack, i.chance(),
                        s -> s.getCommandString() + " % "
                            + Math.round(i.chance() * 100)));
                }
                case MachineProduct.FluidProduct f -> {
                    layout.add(new Part(Kind.FLUID_OUTPUT, group,
                        f.fluid().amount(), true, f.chance()));
                    fluidOutputs.add(stack(f.fluid()));
                }
            }
        }));
        var decomposed = IDecomposedRecipe.builder()
            .with(MachineRecipeComponents.LAYOUT, layout)
            .with(BuiltinRecipeComponents.Processing.TIME, recipe.time());
        if (!items.isEmpty())
            decomposed.with(BuiltinRecipeComponents.Input.INGREDIENTS, items);
        if (!fluids.isEmpty())
            decomposed.with(BuiltinRecipeComponents.Input.FLUID_INGREDIENTS,
                fluids);
        if (!energy.isEmpty())
            decomposed.with(MachineRecipeComponents.ENERGY, energy);
        if (!itemOutputs.isEmpty())
            decomposed.with(BuiltinRecipeComponents.Output.CHANCED_ITEMS,
                itemOutputs);
        if (!fluidOutputs.isEmpty())
            decomposed.with(BuiltinRecipeComponents.Output.FLUIDS,
                fluidOutputs);
        return Optional.of(decomposed.build());
    }

    @Override
    public Optional<MachineRecipeEntry> recompose(
        IRecipeManager<? super MachineRecipeEntry> manager,
        RegistryAccess registryAccess, IDecomposedRecipe decomposed) {
        if (!(manager.getRecipeType() instanceof MachineRecipeType type))
            return Optional.empty();
        var items = cursor(decomposed,
            BuiltinRecipeComponents.Input.INGREDIENTS);
        var fluids = cursor(decomposed,
            BuiltinRecipeComponents.Input.FLUID_INGREDIENTS);
        var energy = cursor(decomposed, MachineRecipeComponents.ENERGY);
        var itemOutputs = cursor(decomposed,
            BuiltinRecipeComponents.Output.CHANCED_ITEMS);
        var fluidOutputs = cursor(decomposed,
            BuiltinRecipeComponents.Output.FLUIDS);
        var inputs = new LinkedHashMap<String, List<MachineIngredient>>();
        var outputs = new LinkedHashMap<String, List<MachineProduct>>();
        for (var part : decomposed.getOrThrow(MachineRecipeComponents.LAYOUT)) {
            switch (part.kind()) {
                case ITEMS -> add(inputs, part.group(),
                    new MachineIngredient.ItemPart(
                        items.next().asVanillaIngredient(), part.count(),
                        part.consumed()));
                case FLUID -> add(inputs, part.group(),
                    fluidPart(fluids.next(), part.consumed(), registryAccess));
                case ENERGY -> add(inputs, part.group(),
                    new MachineIngredient.EnergyPart(energy.next()));
                case ITEM_OUTPUT -> {
                    var product = itemOutputs.next();
                    add(outputs, part.group(),
                        new MachineProduct.ItemProduct(
                            product.getData().getInternal().copy(),
                            (float) product.getPercentage()));
                }
                case FLUID_OUTPUT -> {
                    var fluid = fluidOutputs.next();
                    add(outputs, part.group(),
                        new MachineProduct.FluidProduct(new FluidAmount(
                            fluid.getFluid().builtInRegistryHolder(),
                            net.minecraft.core.component.DataComponentPatch.EMPTY,
                            (int) fluid.getAmount()), part.chance()));
                }
            }
        }
        for (var rest : List.of(items, fluids, energy, itemOutputs,
            fluidOutputs))
            rest.requireDone();
        var recipe = new MachineRecipe(inputs, outputs, decomposed
            .getOrThrowSingle(BuiltinRecipeComponents.Processing.TIME));
        return Optional.of(new MachineRecipeEntry(type, recipe));
    }

    /** A fluid input as a CraftTweaker fluid ingredient, keeping its tag. */
    static CTFluidIngredient fluid(MachineIngredient.FluidPart part) {
        var tag = part.fluids().unwrapKey();
        if (tag.isPresent())
            return new CTFluidIngredient.FluidTagWithAmountIngredient(
                CraftTweakerTagRegistry.INSTANCE
                    .knownTagManager(Registries.FLUID).tag(tag.get().location())
                    .withAmount(part.millibuckets()));
        return part.fluids().stream()
            .<CTFluidIngredient>map(
                holder -> new CTFluidIngredient.FluidStackIngredient(
                    IFluidStack.of(holder.value(), part.millibuckets())))
            .reduce(CTFluidIngredient::asCompound)
            .orElseThrow(() -> new IllegalArgumentException(
                "A fluid input matches no fluid"));
    }

    /**
     * A CraftTweaker fluid ingredient as a fluid input: one fluid, several, or
     * a tag, all with the same amount.
     *
     * @throws IllegalArgumentException
     *             if it mixes tags with fluids or with each other, or its
     *             amounts differ
     */
    static MachineIngredient.FluidPart fluidPart(CTFluidIngredient ingredient,
        boolean consumed, RegistryAccess registries) {
        record Choice(List<Holder<Fluid>> fluids, HolderSet<Fluid> tag,
            int amount) {
        }
        var choice = ingredient.<Choice>mapTo(
            stack -> new Choice(
                List.of(stack.getFluid().builtInRegistryHolder()), null,
                (int) stack.getAmount()),
            (key, amount) -> new Choice(List.of(),
                registries.lookupOrThrow(Registries.FLUID).getOrThrow(key),
                amount),
            choices -> {
                var all = choices.toList();
                if (all.stream().anyMatch(c -> c.tag() != null))
                    throw new IllegalArgumentException(
                        "A machine fluid input is one tag or a list of fluids,"
                            + " not both: " + ingredient.getCommandString());
                if (all.stream().map(Choice::amount).distinct().count() > 1)
                    throw new IllegalArgumentException(
                        "A machine fluid input has one amount for every fluid: "
                            + ingredient.getCommandString());
                return new Choice(
                    all.stream().flatMap(c -> c.fluids().stream())
                        .collect(Collectors.toList()),
                    null, all.isEmpty() ? 0 : all.getFirst().amount());
            });
        var set = choice.tag() != null
            ? choice.tag()
            : HolderSet.direct(choice.fluids());
        return new MachineIngredient.FluidPart(set, choice.amount(), consumed);
    }

    private static IFluidStack stack(FluidAmount fluid) {
        return IFluidStack.of(fluid.fluid().value(), fluid.amount(),
            fluid.components());
    }

    private static <T> void add(Map<String, List<T>> groups, String group,
        T part) {
        groups.computeIfAbsent(group, key -> new ArrayList<>()).add(part);
    }

    private static <C> Cursor<C> cursor(IDecomposedRecipe recipe,
        com.blamejared.crafttweaker.api.recipe.component.IRecipeComponent<C> component) {
        var values = recipe.get(component);
        return new Cursor<>(component.getCommandString(),
            values == null ? List.of() : values);
    }

    /** Reads one component's values in order, as the layout asks for them. */
    private static final class Cursor<C> {
        private final String name;
        private final List<C> values;
        private int next;

        Cursor(String name, List<C> values) {
            this.name = name;
            this.values = values;
        }

        C next() {
            if (next >= values.size())
                throw new IllegalArgumentException(
                    "Too few " + name + " for the machine recipe's layout");
            return values.get(next++);
        }

        void requireDone() {
            if (next != values.size())
                throw new IllegalArgumentException(
                    "Too many " + name + " for the machine recipe's layout");
        }
    }
}
