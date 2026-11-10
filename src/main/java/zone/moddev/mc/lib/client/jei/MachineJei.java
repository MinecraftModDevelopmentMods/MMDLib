package zone.moddev.mc.lib.client.jei;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.gui.client.IngredientDrops;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;

/** Loaded exclusively by the optional JEI plugin. */
final class MachineJei {
    private static IJeiRuntime runtime;
    private static final Set<String> UNKNOWN = new HashSet<>();

    private static Rect2i rect(Rect r) {
        return new Rect2i(r.x(), r.y(), r.width(), r.height());
    }

    static void runtime(IJeiRuntime value) {
        runtime = value;
        // JEI's fixed bottom search/configuration bar does not avoid container
        // exclusion areas.
        MachineScreen.drawerBottomInset = () -> runtime != null
            && runtime.getIngredientListOverlay().isListDisplayed() ? 28 : 4;
        MachineScreen.recipes = (screen, e) -> {
            var types = types(screen, e);
            if (types.isEmpty())
                return false;
            runtime.getRecipesGui().showTypes(types);
            return true;
        };
        MachineScreen.recipeTooltip = (screen,
            e) -> !types(screen, e).isEmpty();
    }

    static void unavailable() {
        runtime = null;
        MachineScreen.drawerBottomInset = () -> 4;
        MachineScreen.recipes = (s, e) -> false;
        MachineScreen.recipeTooltip = (s, e) -> false;
    }

    private static List<RecipeType<?>> types(MachineScreen screen, Element e) {
        if (runtime == null)
            return List.of();
        var result = new ArrayList<RecipeType<?>>();
        for (var id : e.common().jeiRecipes()) {
            var type = runtime.getRecipeManager().getRecipeType(id);
            if (type.isPresent())
                result.add(type.get());
            else if (UNKNOWN
                .add(screen.getMenu().bound.screen().source() + ":" + id))
                MMDLib.LOGGER.warn("Unknown JEI category {} in {}", id,
                    screen.getMenu().bound.screen().source());
        }
        return result;
    }

    // JEI draws its own "Show Recipes" tooltip for the area; merging the
    // element's lines into it
    // keeps the two from being drawn over each other.
    private static IGuiClickableArea clickable(IGuiClickableArea basic,
        MachineScreen screen, Element e, int mx, int my) {
        return new IGuiClickableArea() {
            @Override
            public Rect2i getArea() {
                return basic.getArea();
            }

            @Override
            public void onClick(IFocusFactory focuses, IRecipesGui gui) {
                basic.onClick(focuses, gui);
            }

            @Override
            public void getTooltip(ITooltipBuilder tooltip) {
                screen.tooltipLines(e, mx, my).forEach(tooltip::add);
                basic.getTooltip(tooltip);
            }
        };
    }

    static void gui(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(MachineScreen.class,
            new IGuiContainerHandler<MachineScreen>() {
                @Override
                public List<Rect2i> getGuiExtraAreas(MachineScreen screen) {
                    return screen.exclusions().stream().map(MachineJei::rect)
                        .toList();
                }

                @Override
                public Collection<IGuiClickableArea> getGuiClickableAreas(
                    MachineScreen screen, double mx, double my) {
                    var result = new ArrayList<IGuiClickableArea>();
                    for (var e : screen.getMenu().elements)
                        if (screen.visible(e)
                            && !e.common().jeiRecipes().isEmpty()) {
                            var types = types(screen, e);
                            var r = screen.layout.bounds(e);
                            if (!types.isEmpty()) {
                                var basic = IGuiClickableArea.createBasic(r.x(),
                                    r.y(), r.width(), r.height(),
                                    types.toArray(RecipeType<?>[]::new));
                                result.add(clickable(basic, screen, e, (int) mx,
                                    (int) my));
                            }
                        }
                    return result;
                }
            });
        registration.addGhostIngredientHandler(MachineScreen.class,
            new IGhostIngredientHandler<MachineScreen>() {
                @Override
                public <I> List<Target<I>> getTargetsTyped(MachineScreen screen,
                    ITypedIngredient<I> ingredient, boolean start) {
                    var type = JeiIngredientTypes.GLOBAL
                        .find(ingredient.getType()).orElse(null);
                    if (type == null)
                        return List.of();
                    var result = new ArrayList<Target<I>>();
                    for (var target : IngredientDrops.targets(
                        screen.presentation(), screen.getMenu().elements, type,
                        ingredient.getIngredient())) {
                        result.add(new Target<>() {
                            public Rect2i getArea() {
                                return rect(target.bounds());
                            }

                            public void accept(I value) {
                                if (runtime != null
                                    && net.minecraft.client.Minecraft
                                        .getInstance().screen == screen)
                                    target.accept().test(value);
                            }
                        });
                    }
                    return result;
                }

                @Override
                public void onComplete() {
                }
            });
    }

    static void transfer(IRecipeTransferRegistration registration) {
        var helper = registration.getTransferHelper();
        registration.addUniversalRecipeTransferHandler(
            new IUniversalRecipeTransferHandler<MachineMenu>() {
                public Class<MachineMenu> getContainerClass() {
                    return MachineMenu.class;
                }

                public Optional<MenuType<MachineMenu>> getMenuType() {
                    return Optional.of(ScreenRuntime.MENUS.get());
                }

                public IRecipeTransferError transferRecipe(MachineMenu menu,
                    Object recipe, IRecipeSlotsView slots, Player player,
                    boolean max, boolean transfer) {
                    if (runtime == null
                        || !(menu.blockEntity instanceof ScreenHost host))
                        return helper.createInternalError();
                    var categories = menu.elements.stream()
                        .flatMap(e -> e.common().jeiRecipes().stream())
                        .distinct().toList();
                    for (var id : categories) {
                        var type = runtime.getRecipeManager().getRecipeType(id);
                        if (type.isPresent()
                            && !host.screenRecipeItems(id).isEmpty()
                            && runtime.getRecipeManager()
                                .createRecipeLookup(type.get()).get()
                                .anyMatch(recipe::equals))
                            return delegate(type.get(), helper, menu, recipe,
                                slots, player, max, transfer);
                    }
                    return helper.createInternalError();
                }
            });
    }

    @SuppressWarnings("removal")
    private static <R> IRecipeTransferError delegate(RecipeType<R> type,
        IRecipeTransferHandlerHelper helper, MachineMenu menu, Object recipe,
        IRecipeSlotsView slots, Player player, boolean max, boolean transfer) {
        var info = new IRecipeTransferInfo<MachineMenu, R>() {
            public Class<MachineMenu> getContainerClass() {
                return MachineMenu.class;
            }

            public Optional<MenuType<MachineMenu>> getMenuType() {
                return Optional.of(ScreenRuntime.MENUS.get());
            }

            public RecipeType<R> getRecipeType() {
                return type;
            }

            public boolean canHandle(MachineMenu menu, R recipe) {
                return !getRecipeSlots(menu, recipe).isEmpty();
            }

            public List<Slot> getRecipeSlots(MachineMenu menu, R recipe) {
                if (!(menu.blockEntity instanceof ScreenHost host))
                    return List.of();
                var result = new ArrayList<Slot>();
                for (var address : host.screenRecipeItems(type.getUid())) {
                    int menuIndex = menu.menuSlot(address);
                    var element = menu.slotElement(menuIndex);
                    if (element == null || element.ghost()
                        || !menu.visible(element) || menuIndex < 0
                        || menuIndex >= menu.slots.size())
                        return List.of();
                    result.add(menu.slots.get(menuIndex));
                }
                return result;
            }

            public List<Slot> getInventorySlots(MachineMenu menu, R recipe) {
                return menu.slots.stream()
                    .filter(s -> s.container == player.getInventory()
                        && s.getContainerSlot() < 36 && s.isActive())
                    .toList();
            }
        };
        return helper.createUnregisteredRecipeTransferHandler(info)
            .transferRecipe(menu, type.getRecipeClass().cast(recipe), slots,
                player, max, transfer);
    }
}
