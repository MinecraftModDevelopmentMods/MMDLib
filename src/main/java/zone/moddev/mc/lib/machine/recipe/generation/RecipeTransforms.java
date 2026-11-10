package zone.moddev.mc.lib.machine.recipe.generation;

import java.util.*;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.material.Fluid;

import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;

/**
 * Reusable transformations for a generator's output, before validation and
 * publication.
 */
public final class RecipeTransforms {
    private RecipeTransforms() {
    }

    /**
     * Replaces all FE inputs with one fluid cost, rounded up; keeps other
     * inputs, outputs and time.
     */
    public static MachineRecipe energyToFluid(MachineRecipe recipe,
        String group, HolderSet<Fluid> fluids, long energyPerMillibucket) {
        if (group == null || group.isBlank() || fluids.size() == 0
            || energyPerMillibucket < 1)
            throw new IllegalArgumentException(
                "A fluid group, nonempty fluid set and positive conversion rate are required");
        var inputs = new LinkedHashMap<String, List<MachineIngredient>>();
        long energy = 0;
        for (var entry : recipe.inputs().entrySet()) {
            var remaining = new ArrayList<MachineIngredient>();
            for (var ingredient : entry.getValue()) {
                if (ingredient instanceof MachineIngredient.EnergyPart part) {
                    if (part.amount() < 1)
                        throw new IllegalArgumentException(
                            "Energy must be positive");
                    energy = Math.addExact(energy, part.amount());
                } else
                    remaining.add(ingredient);
            }
            if (!remaining.isEmpty())
                inputs.put(entry.getKey(), remaining);
        }
        if (energy == 0)
            return recipe;
        long amount = energy / energyPerMillibucket
            + (energy % energyPerMillibucket == 0 ? 0 : 1);
        if (amount > Integer.MAX_VALUE)
            throw new IllegalArgumentException(
                "Converted fluid amount exceeds tank range");
        inputs.computeIfAbsent(group, ignored -> new ArrayList<>())
            .add(new MachineIngredient.FluidPart(fluids, (int) amount));
        return new MachineRecipe(inputs, recipe.outputs(), recipe.time());
    }
}
