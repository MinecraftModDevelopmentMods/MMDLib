package com.mcmoddev.lib.integration.jei;

import java.util.stream.Collectors;

import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.registry.CrusherRecipeRegistry;
import com.mcmoddev.lib.registry.recipe.ICrusherRecipe;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.IJeiHelpers;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.api.recipe.IRecipeWrapperFactory;

/**
 *
 * @author Jasmine Iwanek
 * @author Daniel Hazelton
 *
 */
@JEIPlugin
public final class MMDLibJEIPlugin implements IModPlugin {

	public static final String JEI_UID = MMDLib.MODID;
	public static final String RECIPE_UID = JEI_UID + ".crackhammer";
	
	@Override
	public void registerCategories(final IRecipeCategoryRegistration registry) {
		final IGuiHelper guiHelper = registry.getJeiHelpers().getGuiHelper();

		registry.addRecipeCategories(new ICrusherRecipeCategory(guiHelper));
	}

	@Override
	public void register(final IModRegistry registry) {
		IJeiHelpers jeiHelpers = registry.getJeiHelpers();
		
		registry.addRecipes(CrusherRecipeRegistry.getAll().stream().map(rec -> new ICrusherRecipeWrapper(jeiHelpers, rec))
				.collect(Collectors.toList()), RECIPE_UID);
		
		registry.handleRecipes(ICrusherRecipe.class, new IRecipeWrapperFactory<ICrusherRecipe>() {

			@Override
			public IRecipeWrapper getRecipeWrapper(final ICrusherRecipe recipe) {
				return new ICrusherRecipeWrapper(jeiHelpers, recipe);
			}
		}, RECIPE_UID);
	}
}
