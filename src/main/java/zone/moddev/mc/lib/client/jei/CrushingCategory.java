package zone.moddev.mc.lib.client.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;

import zone.moddev.mc.lib.common.recipes.CrushingRecipe;

/** Crackhammer crushing: input slot, a crackhammer filling bottom to top, and the output slot. */
final class CrushingCategory extends AbstractRecipeCategory<RecipeHolder<CrushingRecipe>> {
  static final RecipeType<RecipeHolder<CrushingRecipe>> TYPE =
      RecipeType.createRecipeHolderType(ResourceLocation.parse("mmdlib:crushing"));

  /** 256x256 sheet: background panel, tab icon, and the hammer used for the fill animation. */
  private static final ResourceLocation TEXTURE =
      ResourceLocation.parse("mmdlib:textures/gui/jei/crackhammer.png");

  private static final int WIDTH = 168, HEIGHT = 74;
  private final IDrawableStatic background;
  private final IDrawableAnimated hammer;

  CrushingCategory(IGuiHelper gui) {
    super(
        TYPE,
        Component.translatable("jei.mmdlib.crushing"),
        gui.drawableBuilder(TEXTURE, 169, 1, 16, 16).setTextureSize(256, 256).build(),
        WIDTH,
        HEIGHT);
    background = gui.drawableBuilder(TEXTURE, 0, 0, WIDTH, HEIGHT).setTextureSize(256, 256).build();
    hammer =
        gui.drawableBuilder(TEXTURE, 168, 18, 32, 32)
            .setTextureSize(256, 256)
            .buildAnimated(60, IDrawableAnimated.StartDirection.BOTTOM, false);
  }

  @Override
  public void setRecipe(
      IRecipeLayoutBuilder builder, RecipeHolder<CrushingRecipe> recipe, IFocusGroup focuses) {
    // Slot frames are part of the background texture: an 18x18 input at (41, 14), a 26x26 output at
    // (115, 10).
    builder.addInputSlot(42, 15).addIngredients(recipe.value().getIngredients().getFirst());
    var level = Minecraft.getInstance().level;
    if (level != null)
      builder
          .addOutputSlot(120, 15)
          .addItemStack(recipe.value().getResultItem(level.registryAccess()));
  }

  @Override
  public void draw(
      RecipeHolder<CrushingRecipe> recipe,
      IRecipeSlotsView slots,
      GuiGraphics graphics,
      double mouseX,
      double mouseY) {
    background.draw(graphics);
    hammer.draw(graphics, 71, 8);
    var font = Minecraft.getInstance().font;
    var note = Component.translatable("jei.mmdlib.crushing.mined");
    graphics.drawString(font, note, (WIDTH - font.width(note)) / 2, 52, 0xFF404040, false);
  }
}
