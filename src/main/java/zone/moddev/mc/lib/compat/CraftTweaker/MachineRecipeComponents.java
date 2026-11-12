package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.Objects;

import com.blamejared.crafttweaker.api.recipe.component.IRecipeComponent;

import com.google.gson.reflect.TypeToken;

import net.minecraft.resources.ResourceLocation;

/**
 * The CraftTweaker recipe components a machine recipe decomposes into beside
 * the built-in ones: each part's place in the recipe, and its energy. Items,
 * fluids, outputs and time use CraftTweaker's own components, so its ingredient
 * replacement reaches them.
 */
final class MachineRecipeComponents {
    /** What a part is. */
    enum Kind {
        /** An item input. */
        ITEMS,
        /** A fluid input. */
        FLUID,
        /** An energy input. */
        ENERGY,
        /** An item output. */
        ITEM_OUTPUT,
        /** A fluid output. */
        FLUID_OUTPUT
    }

    /**
     * One part of a machine recipe, in recipe order: its kind, group, item
     * count, whether an input is consumed, and an output's chance. Its value is
     * the next entry of the matching built-in component (or of
     * {@link #ENERGY}).
     */
    record Part(Kind kind, String group, int count, boolean consumed,
        float chance) {
        Part {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(group);
        }
    }

    /** The parts, in order. */
    static final IRecipeComponent<Part> LAYOUT = IRecipeComponent.simple(
        ResourceLocation.fromNamespaceAndPath("mmdlib", "machine/layout"),
        new TypeToken<>() {
        }, Objects::equals);

    /** Energy inputs, in FE per cycle. */
    static final IRecipeComponent<Long> ENERGY = IRecipeComponent.simple(
        ResourceLocation.fromNamespaceAndPath("mmdlib", "machine/energy"),
        new TypeToken<>() {
        }, Objects::equals);

    private MachineRecipeComponents() {
    }
}
