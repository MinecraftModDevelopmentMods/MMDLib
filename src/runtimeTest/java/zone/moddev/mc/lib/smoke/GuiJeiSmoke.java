package zone.moddev.mc.lib.smoke;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.gui.client.MachineScreen;

/** Invoked only when JEI is present, through its registered runtime hooks. */
public final class GuiJeiSmoke {
    static void transferOnly(MachineScreen screen) throws Exception {
        var id = net.minecraft.resources.ResourceLocation.parse("mmdlib:crushing");
        var host = (zone.moddev.mc.lib.gui.runtime.ScreenHost) screen.getMenu().blockEntity;
        if (host.screenRecipeSlots(id).length != 0 || host.screenRecipeItems(id).isEmpty())
            throw new AssertionError("Fixture must expose named recipe destinations only");
        var field = Class.forName("zone.moddev.mc.lib.client.jei.MachineJei")
            .getDeclaredField("runtime");
        field.setAccessible(true);
        var runtime = (IJeiRuntime) field.get(null);
        var type = runtime
            .getRecipeManager()
            .getRecipeType(id, net.minecraft.world.item.crafting.RecipeHolder.class)
            .orElseThrow();
        checkTransfer(runtime, screen, type);
    }

    /** Uses JEI's registered target, including the actual integration's accept callback. */
    @SuppressWarnings("removal")
    static java.util.Optional<java.util.function.Consumer<ItemStack>> ghostTarget(
        MachineScreen screen, ItemStack stack) throws Exception {
        return ingredientTarget(screen, "ghost", VanillaTypes.ITEM_STACK, stack);
    }

    @SuppressWarnings("removal")
    static <T> java.util.Optional<java.util.function.Consumer<T>> ingredientTarget(
        MachineScreen screen,
        String elementId,
        mezz.jei.api.ingredients.IIngredientType<T> type,
        T stack)
        throws Exception {
        var field = Class.forName("zone.moddev.mc.lib.client.jei.MachineJei")
            .getDeclaredField("runtime");
        field.setAccessible(true);
        var runtime = (IJeiRuntime) field.get(null);
        if (runtime == null)
            throw new AssertionError("JEI runtime unavailable");
        var ingredient = runtime.getIngredientManager().createTypedIngredient(type, stack, false)
            .orElseThrow();
        var bounds = screen.bounds(screen.getMenu().bound.screen().find(elementId).orElseThrow());
        for (var handler : runtime.getScreenHelper().getGhostIngredientHandlers(screen))
            for (var target : handler.getTargetsTyped(screen, ingredient, true))
                if (target.getArea().contains(bounds.x() + 2, bounds.y() + 2))
                    return java.util.Optional.of(
                        value -> {
                            target.accept(value);
                            handler.onComplete();
                        });
        return java.util.Optional.empty();
    }

    @SuppressWarnings("removal")
    public static void check(MachineScreen screen) throws Exception {
        var field = Class.forName("zone.moddev.mc.lib.client.jei.MachineJei")
            .getDeclaredField("runtime");
        field.setAccessible(true);
        var runtime = (IJeiRuntime) field.get(null);
        if (runtime == null)
            throw new AssertionError("JEI runtime unavailable");
        var menu = screen.getMenu();
        if (runtime.getScreenHelper().getGuiExclusionAreas(screen).count() < screen.exclusions()
            .size())
            throw new AssertionError("JEI exclusions missing");
        var progress = menu.bound.screen().find("progress").orElseThrow();
        var p = screen.layout.bounds(progress);
        var area = runtime
            .getScreenHelper()
            .getGuiClickableArea(screen, p.x() + 1, p.y() + 1)
            .findFirst()
            .orElseThrow(() -> new AssertionError("JEI recipe click area missing"));
        area.onClick(runtime.getJeiHelpers().getFocusFactory(), runtime.getRecipesGui());
        Minecraft.getInstance().setScreen(screen);
        var ingredient = runtime
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
        if (!accepted)
            throw new AssertionError("JEI ghost target missing");
        var manager = runtime.getRecipeManager();
        var type = manager
            .getRecipeType(
                net.minecraft.resources.ResourceLocation.parse("mmdlib:crushing"),
                net.minecraft.world.item.crafting.RecipeHolder.class)
            .orElseThrow();
        checkTransfer(runtime, screen, type);
        checkMachineCategory(runtime);
        MMDLib.LOGGER.info(
            "MMDLIB_GUI_JEI_HOOKS_PASS: registered exclusion areas, recipe click area, ghost drag"
                + " target and recipe transfer request");
    }

    /**
     * The generic category for {@code mmdlib_smoke:grinding} lists the two test recipes that can
     * run, not the two whose inputs name nothing; the copper one lays out an ore and a water input
     * and two products; the iron one's water tag shows source water, not flowing water.
     */
    private static void checkMachineCategory(IJeiRuntime runtime) {
        var manager = runtime.getRecipeManager();
        var type = zone.moddev.mc.lib.client.jei.MachineRecipeCategory.recipeType(
            MachineRecipeTypeSmoke.GRINDING);
        var category = manager.getRecipeCategory(type);
        var recipes = manager.createRecipeLookup(type).get().toList();
        if (recipes.size() != 2)
            throw new AssertionError("JEI machine recipes: " + recipes.size());
        var copper = recipes.stream()
            .filter(holder -> holder.id().getPath().equals("grinding/copper"))
            .findFirst()
            .orElseThrow();
        var slots = manager
            .createRecipeLayoutDrawable(
                category, copper, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup())
            .orElseThrow(() -> new AssertionError("JEI machine layout failed"))
            .getRecipeSlotsView();
        int inputs = slots.getSlotViews(mezz.jei.api.recipe.RecipeIngredientRole.INPUT).size();
        int outputs = slots.getSlotViews(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT).size();
        if (inputs != 2 || outputs != 2)
            throw new AssertionError("JEI machine slots: " + inputs + " in, " + outputs + " out");
        var iron = recipes.stream()
            .filter(holder -> holder.id().getPath().equals("grinding/iron"))
            .findFirst()
            .orElseThrow();
        var ironSlots = manager
            .createRecipeLayoutDrawable(
                category, iron, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup())
            .orElseThrow(() -> new AssertionError("JEI machine layout failed"))
            .getRecipeSlotsView();
        var inputFluids = ironSlots.getSlotViews(mezz.jei.api.recipe.RecipeIngredientRole.INPUT)
            .stream()
            .flatMap(view -> view.getAllIngredients())
            .map(mezz.jei.api.ingredients.ITypedIngredient::getIngredient)
            .filter(net.neoforged.neoforge.fluids.FluidStack.class::isInstance)
            .map(net.neoforged.neoforge.fluids.FluidStack.class::cast)
            .toList();
        if (inputFluids.size() != 1
            || inputFluids.getFirst().getFluid() != net.minecraft.world.level.material.Fluids.WATER)
            throw new AssertionError("JEI water tag shows: " + inputFluids);
        MMDLib.LOGGER.info(
            "MMDLIB_GUI_JEI_MACHINE_PASS: generic machine recipe category with {}x{} layout",
            category.getWidth(),
            category.getHeight());
    }

    /** Opens JEI's recipe screen on the grinding category, for the screenshot. */
    static void showMachineRecipes() throws Exception {
        var field = Class.forName("zone.moddev.mc.lib.client.jei.MachineJei")
            .getDeclaredField("runtime");
        field.setAccessible(true);
        var runtime = (IJeiRuntime) field.get(null);
        runtime
            .getRecipesGui()
            .showTypes(
                java.util.List.of(
                    zone.moddev.mc.lib.client.jei.MachineRecipeCategory.recipeType(
                        MachineRecipeTypeSmoke.GRINDING)));
    }

    @SuppressWarnings("removal")
    private static <R> void checkTransfer(
        IJeiRuntime runtime, MachineScreen screen, mezz.jei.api.recipe.RecipeType<R> type) {
        var manager = runtime.getRecipeManager();
        var category = manager.getRecipeCategory(type);
        var recipe = manager.createRecipeLookup(type).get().findFirst().orElseThrow();
        var layout = manager
            .createRecipeLayoutDrawable(
                category, recipe, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup())
            .orElseThrow();
        var handler = runtime
            .getRecipeTransferManager()
            .getRecipeTransferHandler(screen.getMenu(), category)
            .orElseThrow(() -> new AssertionError("JEI transfer handler missing"));
        var error = handler.transferRecipe(
            screen.getMenu(),
            recipe,
            layout.getRecipeSlotsView(),
            Minecraft.getInstance().player,
            false,
            false);
        if (error != null)
            throw new AssertionError("JEI transfer validation failed: " + error);
        // The same ingredient in the offhand must not be consumed as a recipe input.
        var inventory = Minecraft.getInstance().player.getInventory();
        var ingredient = inventory.getItem(9);
        var previousOffhand = inventory.getItem(40);
        try {
            inventory.setItem(9, ItemStack.EMPTY);
            inventory.setItem(40, ingredient);
            var equippedOnly = handler.transferRecipe(
                screen.getMenu(),
                recipe,
                layout.getRecipeSlotsView(),
                Minecraft.getInstance().player,
                false,
                false);
            if (equippedOnly == null)
                throw new AssertionError("JEI used the offhand as recipe storage");
        } finally {
            inventory.setItem(9, ingredient);
            inventory.setItem(40, previousOffhand);
        }
        error = handler.transferRecipe(
            screen.getMenu(),
            recipe,
            layout.getRecipeSlotsView(),
            Minecraft.getInstance().player,
            false,
            true);
        if (error != null)
            throw new AssertionError("JEI transfer request failed: " + error);
    }
}
