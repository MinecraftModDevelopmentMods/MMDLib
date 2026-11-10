package zone.moddev.mc.lib.gui.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;

/** Shared ingredient identities; integrations register their viewer-specific mappings. */
public final class IngredientTypes {
    private IngredientTypes() {
    }

    public static final IngredientType<ItemStack> ITEM = new IngredientType<>(
        ResourceLocation.parse("mmdlib:item"), ItemStack.class, ItemStack::copy);
    public static final IngredientType<FluidStack> FLUID = new IngredientType<>(
        ResourceLocation.parse("mmdlib:fluid"), FluidStack.class, FluidStack::copy);
}
