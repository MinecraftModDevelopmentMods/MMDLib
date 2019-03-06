package com.mcmoddev.lib.integration.jei;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import com.mcmoddev.lib.MMDLib;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IDrawableAnimated;
import mezz.jei.api.gui.IDrawableStatic;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;

public class ICrusherRecipeCategory implements IRecipeCategory<ICrusherRecipeWrapper> {

	private final ResourceLocation resourceLocation = new ResourceLocation(MMDLib.MODID,
			"textures/jei/jeihammeroverlay.png");
	private final IDrawable background;
	private final IDrawable icon;
	private final IDrawableAnimated hammer;

	/**
	 *
	 * @param guiHelper
	 */
	public ICrusherRecipeCategory(final IGuiHelper guiHelper) {
		this.background = guiHelper.createDrawable(this.resourceLocation, 0, 0, 166, 130);
		this.icon = guiHelper.createDrawable(this.resourceLocation, 170, 2, 16, 16);

		final IDrawableStatic hammerDrawable = guiHelper.createDrawable(this.resourceLocation, 169,
				17, 32, 32);
		this.hammer = guiHelper.createAnimatedDrawable(hammerDrawable, 100,
				IDrawableAnimated.StartDirection.BOTTOM, false);
	}

	@Override
	public String getUid() {
		return MMDLibJEIPlugin.RECIPE_UID;
	}

	@Override
	public String getModName() {
		return MMDLibJEIPlugin.JEI_UID;
	}

	@Override
	public String getTitle() {
		return new TextComponentTranslation(String.format(this.getUid())).getFormattedText();
	}

	@Override
	public IDrawable getBackground() {
		return this.background;
	}

	@Override
	public IDrawable getIcon() {
		return this.icon;
	}

	@Override
	public void setRecipe(final IRecipeLayout recipeLayout,
			@Nonnull final ICrusherRecipeWrapper recipeWrapper, final IIngredients ingredients) {
		final IGuiItemStackGroup guiItemStacks = recipeLayout.getItemStacks();

		// init the input slot
		guiItemStacks.init(0, true, 42, 14);

		// init the output slot
		guiItemStacks.init(1, false, 119, 14);

		// load the output and input bits
		final List<List<ItemStack>> inputs = ingredients.getInputs(VanillaTypes.ITEM);
		final List<List<ItemStack>> outputs = ingredients.getOutputs(VanillaTypes.ITEM);

		// setup the data
		guiItemStacks.set(0, inputs.get(0));
		guiItemStacks.set(1, outputs.get(0));
	}

	@Override
	public void drawExtras(final Minecraft minecraft) {
		this.hammer.draw(minecraft, 71, 6);
	}

	@Override
	public List<String> getTooltipStrings(final int mouseX, final int mouseY) {
		return Collections.<String>emptyList();
	}
}
