package zone.moddev.mc.lib.client.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import zone.moddev.mc.lib.gui.client.recipe.RecipePresentation;

/** Optional JEI adapter for the same presentations used by recipe drawers. */
public abstract class PresentedRecipeCategory<T>
    extends
        AbstractRecipeCategory<T> {
    protected PresentedRecipeCategory(RecipeType<T> type, Component title,
        IDrawable icon, int width, int height) {
        super(type, title, icon, width, height);
    }

    public abstract RecipePresentation presentation(T recipe);

    private static RecipeIngredientRole role(RecipePresentation.Role role) {
        return RecipeIngredientRole.valueOf(role.name());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, T recipe,
        IFocusGroup focuses) {
        var view = presentation(recipe);
        for (var item : view.items())
            builder.addSlot(role(item.role()), item.x(), item.y())
                .addItemStack(item.stack()).addRichTooltipCallback(
                    (slot, tooltip) -> item.tooltip().forEach(tooltip::add));
        for (var fluid : view.fluids())
            builder.addInvisibleIngredients(role(fluid.role()))
                .addIngredient(NeoForgeTypes.FLUID_STACK, fluid.stack());
    }

    @Override
    public void draw(T recipe, IRecipeSlotsView slots, GuiGraphics graphics,
        double x, double y) {
        presentation(recipe).drawBackground(graphics);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, T recipe,
        IFocusGroup focuses) {
        for (var tooltip : presentation(recipe).tooltips()) {
            var r = tooltip.bounds();
            builder.addTooltipArea(r.x(), r.y(), r.width(), r.height())
                .setTooltip(tooltip.lines());
        }
    }
}
