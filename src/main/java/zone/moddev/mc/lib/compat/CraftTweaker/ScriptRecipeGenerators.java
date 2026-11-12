package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.machine.recipe.generation.MachineRecipeGenerator;

/**
 * Recipe generators registered by scripts, by ID, for
 * {@link ScriptRecipeGenerator} to run. Free of CraftTweaker types, so a
 * machine naming the generator class fails with a plain message when
 * CraftTweaker is missing.
 */
final class ScriptRecipeGenerators {
    private static final Map<ResourceLocation, MachineRecipeGenerator> generators = new ConcurrentHashMap<>();

    private ScriptRecipeGenerators() {
    }

    /**
     * Registers {@code generator} as {@code id}.
     *
     * @throws IllegalArgumentException if {@code id} is already registered
     */
    static void register(ResourceLocation id,
        MachineRecipeGenerator generator) {
        if (generators.putIfAbsent(id, generator) != null)
            throw new IllegalArgumentException(
                "Duplicate script recipe generator " + id);
    }

    /** The generator registered as {@code id}, if any. */
    static Optional<MachineRecipeGenerator> get(ResourceLocation id) {
        return Optional.ofNullable(generators.get(id));
    }
}
