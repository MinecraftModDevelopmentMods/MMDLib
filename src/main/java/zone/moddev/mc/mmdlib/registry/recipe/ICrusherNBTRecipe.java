package zone.moddev.mc.mmdlib.registry.recipe;

import net.minecraft.item.ItemStack;

public interface ICrusherNBTRecipe {
	public ItemStack getOutput(ItemStack input);
}
