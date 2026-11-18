package zone.moddev.mc.lib.machine.feature;

import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.machine.ingredient.FluidAmount;

/**
 * Mod-owned external insertion rules, evaluated against current config/recipes.
 * Queries must not mutate the host; they also run during simulated transfers.
 * The member is its stable XML slot/tank ID. The host may have no level yet.
 * Inputs are detached. Existing contents remain extractable when rules change.
 */
public interface StorageAdmission {
    default boolean acceptsItem(ProcessorMachine host, String member,
        ItemStack stack) {
        return false;
    }

    default boolean acceptsFluid(ProcessorMachine host, String member,
        FluidAmount fluid) {
        return false;
    }
}
