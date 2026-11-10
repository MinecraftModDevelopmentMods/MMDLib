package zone.moddev.mc.lib.machine.neoforge;

import java.util.*;
import java.util.function.Supplier;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;

import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.inventory.*;
import zone.moddev.mc.lib.machine.recipe.*;

/**
 * Admission tests depend on recipe ingredients, never on currently available
 * amounts or outputs.
 */
public final class RecipeInputFilters {
    public record Link(MachineRecipeType type, String group) {
    }

    private final Supplier<Level> level;
    private final MachineInventory inventory;
    private RecipeManager manager;
    private Collection<RecipeHolder<?>> snapshot;
    private final Map<MachineRecipeType, List<MachineRecipe>> recipes = new HashMap<>();
    private final Map<Link, Accepted> accepted = new HashMap<>();

    private record Accepted(Set<Item> items, List<Ingredient> custom,
        Set<Fluid> fluids) {
        boolean accepts(ItemStack stack) {
            return items.contains(stack.getItem()) || custom.stream()
                .anyMatch(ingredient -> ingredient.test(stack));
        }
    }

    public RecipeInputFilters(Supplier<Level> level,
        MachineInventory inventory) {
        this.level = level;
        this.inventory = inventory;
    }

    public boolean acceptsItem(List<Link> links, ItemStack stack) {
        if (!refresh())
            return false;
        for (var link : links)
            if (accepted.computeIfAbsent(link, this::index).accepts(stack))
                return true;
        return false;
    }

    public boolean acceptsFluid(List<Link> links, FluidStack stack) {
        if (!refresh())
            return false;
        for (var link : links)
            if (accepted.computeIfAbsent(link, this::index).fluids()
                .contains(stack.getFluid()))
                return true;
        return false;
    }

    private boolean refresh() {
        var world = level.get();
        if (world == null)
            return false;
        var current = world.getRecipeManager();
        // 1.21.1 exposes its immutable by-name map's values without copying.
        // apply/replaceRecipes
        // replace that map on server reload and client synchronization, even
        // when IDs stay the
        // same.
        var currentSnapshot = current.getRecipes();
        if (manager != current || snapshot != currentSnapshot) {
            manager = current;
            snapshot = currentSnapshot;
            recipes.clear();
            accepted.clear();
        }
        return true;
    }

    private Accepted index(Link link) {
        var items = new HashSet<Item>();
        var custom = new ArrayList<Ingredient>();
        var fluids = new HashSet<Fluid>();
        var matching = recipes.computeIfAbsent(link.type(),
            type -> type.recipes(manager).stream()
                .map(holder -> holder.value().recipe()).filter(this::compatible)
                .toList());
        for (var recipe : matching)
            for (var part : recipe.inputs().getOrDefault(link.group(),
                List.of())) {
                if (part instanceof MachineIngredient.ItemPart item) {
                    var ingredient = item.items();
                    if (ingredient.isSimple()) {
                        for (var stack : ingredient.getItems())
                            items.add(stack.getItem());
                    } else
                        custom.add(ingredient);
                } else if (part instanceof MachineIngredient.FluidPart fluid)
                    fluid.fluids()
                        .forEach(holder -> fluids.add(holder.value()));
            }
        return new Accepted(Set.copyOf(items), List.copyOf(custom),
            Set.copyOf(fluids));
    }

    private boolean compatible(MachineRecipe recipe) {
        for (var entry : recipe.inputs().entrySet()) {
            var group = inventory.group(entry.getKey()).orElse(null);
            if (group == null)
                return false;
            for (var part : entry.getValue()) {
                if (!(part instanceof MachineIngredient.ItemPart
                    && group instanceof ItemGroup)
                    && !(part instanceof MachineIngredient.FluidPart
                        && group instanceof FluidGroup)
                    && !(part instanceof MachineIngredient.EnergyPart
                        && group instanceof EnergyGroup))
                    return false;
            }
        }
        for (var entry : recipe.outputs().entrySet()) {
            var group = inventory.group(entry.getKey()).orElse(null);
            if (group == null)
                return false;
            for (var product : entry.getValue())
                if (!(product instanceof MachineProduct.ItemProduct
                    && group instanceof ItemGroup)
                    && !(product instanceof MachineProduct.FluidProduct
                        && group instanceof FluidGroup))
                    return false;
        }
        return true;
    }
}
