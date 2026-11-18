package zone.moddev.mc.companion;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StorageAdmission;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;
import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

/** A custom processor can also supply live insertion rules without casts. */
public final class AdmissionDemo extends StoredProcessor
    implements StorageAdmission {
    private static final ResourceLocation STILL_RECIPE_TYPE = ResourceLocation
        .parse("mmdlib_companion:lava_smelting");

    /** A consuming mod can supply this value from its own reloadable config. */
    public static volatile ResourceLocation fuelTag = ResourceLocation
        .parse("minecraft:lava");

    @Override
    public boolean acceptsItem(ProcessorMachine host, String member,
        ItemStack stack) {
        if (member.startsWith("fuel/"))
            return stack.getBurnTime(RecipeType.SMELTING) > 0;
        return host.level() != null
            && host.level().getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                new SingleRecipeInput(stack), host.level()).isPresent();
    }

    @Override
    public boolean acceptsFluid(ProcessorMachine host, String member,
        FluidAmount fluid) {
        if (member.equals("oil"))
            return fluid.fluid().is(TagKey.create(Registries.FLUID, fuelTag));
        if (host.level() == null)
            return false;
        // Query the current manager by type: bounded work and reload-safe.
        return MachineContent.recipeType(STILL_RECIPE_TYPE)
            .recipes(host.level().getRecipeManager()).stream()
            .anyMatch(holder -> holder.value().recipe().inputs()
                .getOrDefault("fuel", java.util.List.of()).stream().anyMatch(
                    part -> part instanceof MachineIngredient.FluidPart input
                        && input.fluids().contains(fluid.fluid())));
    }

    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine context) {
        return false;
    }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine context,
        int ticks) {
        return ProcessState.IDLE;
    }
}
