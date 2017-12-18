package com.mcmoddev.lib.registry;

import java.util.Iterator;
import java.util.List;

import javax.annotation.Nonnull;

import com.google.common.collect.Lists;
import com.mcmoddev.lib.registry.recipe.ArbitraryCrusherRecipe;
import com.mcmoddev.lib.registry.recipe.ICrusherRecipe;
import com.mcmoddev.lib.registry.recipe.OreDictionaryCrusherRecipe;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.IForgeRegistryModifiable;
import net.minecraftforge.registries.RegistryBuilder;

/**
 * Original code by Dr. Cyano (aka: cyanobacterium on GitHub)
 * Rewritten entirely as part of the 1.12 porting by D. Hazelton, 20-SEP-2017
 */
public class CrusherRecipeRegistry {

	private final IForgeRegistryModifiable<ICrusherRecipe> registry;
	private static final CrusherRecipeRegistry instance = new CrusherRecipeRegistry();

	private CrusherRecipeRegistry() {
		this.registry = (IForgeRegistryModifiable<ICrusherRecipe>) new RegistryBuilder<ICrusherRecipe>()
				.setName(new ResourceLocation("mmdlib", "crusher_registry")).allowModification()
				.setType(ICrusherRecipe.class).setMaxID(Integer.MAX_VALUE >> 4).create();
	}

	// back compat add-recipe stuff
	public static void addNewCrusherRecipe(@Nonnull final String oreDict, @Nonnull final ItemStack output) {
		instance.register(new OreDictionaryCrusherRecipe(oreDict, output));
	}

	public static void addNewCrusherRecipe(@Nonnull final Block input, @Nonnull final ItemStack output) {
		instance.register(new ArbitraryCrusherRecipe(input, output));
	}

	public static void addNewCrusherRecipe(@Nonnull final ItemStack input, @Nonnull final ItemStack output) {
		instance.register(new ArbitraryCrusherRecipe(input, output));
	}

	public static void addNewCrusherRecipe(@Nonnull final Item input, @Nonnull final ItemStack output) {
		instance.register(new ArbitraryCrusherRecipe(input, output));
	}

	// proper entry point
	public void register(@Nonnull final ICrusherRecipe recipe) {
		this.registry.register(recipe);
	}

	// more back-compat
	public static List<ICrusherRecipe> getAll() {
		return Lists.newArrayList(instance.registry.iterator());
	}

	public static CrusherRecipeRegistry getInstance() {
		return instance;
	}

	public void clearCache() {
		// we do nothing - back-compat
	}

	// very nice to have helpers

	// get an ICrusherRecipe for a given input, if one exists
	public static ICrusherRecipe getRecipeForInputItem(@Nonnull final ItemStack itemStack) {
		Iterator<ICrusherRecipe> iter = instance.registry.iterator();

		while (iter.hasNext()) {
			ICrusherRecipe cur = iter.next();
			if (cur.isValidInput(itemStack)) {
				return cur;
			}
		}

		return null;
	}

	// if a recipe that can take the matching input exists, give us the name
	public static ResourceLocation getNameForInputItem(@Nonnull final ItemStack itemStack) {
		ICrusherRecipe r = getRecipeForInputItem(itemStack);

		if (r != null) {
			return instance.registry.getKey(r);
		}

		return new ResourceLocation("");
	}

	// removing recipes!
	// statics are for convenience and convenient naming

	// remove a single entry, by resource location/name
	public static void removeByName(@Nonnull final ResourceLocation name) {
		instance.remove(name);
	}

	// remove all entries that map to the ore-dict
	public static void removeByInput(@Nonnull final String oreDictName) {
		NonNullList<ItemStack> itemStacks = OreDictionary.getOres(oreDictName);

		for (ItemStack itemStack : itemStacks) {
			removeByName(getNameForInputItem(itemStack));
		}
	}

	// remove single item, by Item
	public static void removeByInput(@Nonnull final Item input) {
		removeByInput(new ItemStack(input));

	}

	// remove single item, by Block
	public static void removeByInput(@Nonnull final Block input) {
		removeByInput(Item.getItemFromBlock(input));
	}

	// remove single item, by ItemStack
	public static void removeByInput(@Nonnull final ItemStack input) {
		removeByName(getNameForInputItem(input));
	}

	// actually do the removal call
	public void remove(@Nonnull final ResourceLocation name) {
		this.registry.remove(name);
	}

}
