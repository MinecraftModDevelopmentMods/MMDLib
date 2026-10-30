package zone.moddev.mc.lib.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.*;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.gui.client.MachineScreen;

/** Invoked only when JEI is present, through its registered runtime hooks. */
public final class GuiJeiSmoke {
  @SuppressWarnings("removal")
  public static void check(MachineScreen screen) throws Exception {
    var field =
        Class.forName("zone.moddev.mc.lib.client.jei.MachineJei").getDeclaredField("runtime");
    field.setAccessible(true);
    var runtime = (IJeiRuntime) field.get(null);
    if (runtime == null) throw new AssertionError("JEI runtime unavailable");
    var menu = screen.getMenu();
    if (runtime.getScreenHelper().getGuiExclusionAreas(screen).count() < screen.exclusions().size())
      throw new AssertionError("JEI exclusions missing");
    var progress = menu.bound.screen().find("progress").orElseThrow();
    var p = screen.layout.bounds(progress);
    var area =
        runtime
            .getScreenHelper()
            .getGuiClickableArea(screen, p.x() + 1, p.y() + 1)
            .findFirst()
            .orElseThrow(() -> new AssertionError("JEI recipe click area missing"));
    area.onClick(runtime.getJeiHelpers().getFocusFactory(), runtime.getRecipesGui());
    Minecraft.getInstance().setScreen(screen);
    var ingredient =
        runtime
            .getIngredientManager()
            .createTypedIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.GOLD_INGOT), false)
            .orElseThrow();
    var ghost = menu.bound.screen().find("ghost").orElseThrow();
    var g = screen.bounds(ghost);
    boolean accepted = false;
    for (var handler : runtime.getScreenHelper().getGhostIngredientHandlers(screen))
      for (var target : handler.getTargetsTyped(screen, ingredient, true)) {
        if (target.getArea().contains(g.x() + 2, g.y() + 2)) {
          target.accept(ingredient.getIngredient());
          handler.onComplete();
          accepted = true;
        }
      }
    if (!accepted) throw new AssertionError("JEI ghost target missing");
    var manager = runtime.getRecipeManager();
    var type =
        manager
            .getRecipeType(
                net.minecraft.resources.ResourceLocation.parse("mmdlib:crushing"),
                net.minecraft.world.item.crafting.RecipeHolder.class)
            .orElseThrow();
    checkTransfer(runtime, screen, type);
    MMDLib.LOGGER.info(
        "MMDLIB_GUI_JEI_HOOKS_PASS: registered exclusion areas, recipe click area, ghost drag"
            + " target and recipe transfer request");
  }

  @SuppressWarnings("removal")
  private static <R> void checkTransfer(
      IJeiRuntime runtime, MachineScreen screen, mezz.jei.api.recipe.RecipeType<R> type) {
    var manager = runtime.getRecipeManager();
    var category = manager.getRecipeCategory(type);
    var recipe = manager.createRecipeLookup(type).get().findFirst().orElseThrow();
    var layout =
        manager
            .createRecipeLayoutDrawable(
                category, recipe, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup())
            .orElseThrow();
    var handler =
        runtime
            .getRecipeTransferManager()
            .getRecipeTransferHandler(screen.getMenu(), category)
            .orElseThrow(() -> new AssertionError("JEI transfer handler missing"));
    var error =
        handler.transferRecipe(
            screen.getMenu(),
            recipe,
            layout.getRecipeSlotsView(),
            Minecraft.getInstance().player,
            false,
            false);
    if (error != null) throw new AssertionError("JEI transfer validation failed: " + error);
    error =
        handler.transferRecipe(
            screen.getMenu(),
            recipe,
            layout.getRecipeSlotsView(),
            Minecraft.getInstance().player,
            false,
            true);
    if (error != null) throw new AssertionError("JEI transfer request failed: " + error);
  }
}
