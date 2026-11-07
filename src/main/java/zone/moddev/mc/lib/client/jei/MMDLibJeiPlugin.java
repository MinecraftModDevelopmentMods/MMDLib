package zone.moddev.mc.lib.client.jei;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.api.MaterialEventTypes;
import zone.moddev.mc.lib.common.recipes.CrushingRecipe;
import zone.moddev.mc.lib.common.runtime.CrackhammerEvents;
import zone.moddev.mc.lib.common.runtime.ItemEventBridge;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/**
 * Optional JEI integration. Only JEI loads this class (via {@link JeiPlugin}); nothing else in
 * MMDLib references it, so MMDLib runs without JEI installed.
 */
@JeiPlugin
public final class MMDLibJeiPlugin implements IModPlugin {
  /** Called by JEI, which finds the plugin through {@link JeiPlugin}. */
  public MMDLibJeiPlugin() {}

  @Override
  public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration registration) {
    MachineJei.gui(registration);
  }

  @Override
  public void registerRecipeTransferHandlers(
      mezz.jei.api.registration.IRecipeTransferRegistration registration) {
    MachineJei.transfer(registration);
  }

  @Override
  public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
    MachineJei.runtime(runtime);
  }

  @Override
  public void onRuntimeUnavailable() {
    MachineJei.unavailable();
  }

  @Override
  public ResourceLocation getPluginUid() {
    return ResourceLocation.parse("mmdlib:jei");
  }

  @Override
  public void registerCategories(IRecipeCategoryRegistration registration) {
    registration.addRecipeCategories(
        new CrushingCategory(registration.getJeiHelpers().getGuiHelper()));
    var level = Minecraft.getInstance().level;
    for (var type : machineRecipeTypes())
      registration.addRecipeCategories(
          MachineRecipeCategory.create(
              type,
              level == null
                  ? List.of()
                  : MachineRecipeCategory.shown(type.recipes(level.getRecipeManager()))));
  }

  @Override
  public void registerRecipes(IRecipeRegistration registration) {
    var level = Minecraft.getInstance().level;
    registration.addRecipes(
        CrushingCategory.TYPE,
        level == null
            ? List.of()
            : level.getRecipeManager().getAllRecipesFor(CrushingRecipe.TYPE.get()));
    if (level != null)
      for (var type : machineRecipeTypes())
        registration.addRecipes(
            MachineRecipeCategory.recipeType(type),
            MachineRecipeCategory.shown(type.recipes(level.getRecipeManager())));
  }

  /** Every registered machine recipe type, whichever mod registered it. */
  private static List<MachineRecipeType> machineRecipeTypes() {
    return BuiltInRegistries.RECIPE_TYPE.stream()
        .filter(MachineRecipeType.class::isInstance)
        .map(MachineRecipeType.class::cast)
        .toList();
  }

  /**
   * Catalysts are found by binding, not class: any item bound to a crackhammer handler, addon items
   * included.
   */
  @Override
  public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
    zone.moddev.mc.lib.machine.neoforge.MachineContent.definitions()
        .forEach(
            (id, machine) ->
                registration.addRecipeCatalyst(
                    new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(id)),
                    MachineRecipeCategory.recipeType(
                        zone.moddev.mc.lib.machine.neoforge.MachineContent.recipeType(
                            machine.recipeType().id()))));
    var materials = MMDLib.itemMaterials();
    MMDLib.itemDefinitions()
        .forEach(
            (id, definition) -> {
              var material = materials.get(id);
              boolean crackhammer =
                  CrackhammerEvents.CRUSH.equals(
                          ItemEventBridge.binding(
                              definition.events(),
                              material,
                              MaterialEventTypes.TOOL_RIGHT_CLICK.name()))
                      || CrackhammerEvents.HARVEST.equals(
                          ItemEventBridge.binding(
                              definition.events(),
                              material,
                              MaterialEventTypes.TOOL_HARVEST.name()));
              if (crackhammer)
                BuiltInRegistries.ITEM
                    .getOptional(id)
                    .ifPresent(
                        item -> registration.addRecipeCatalysts(CrushingCategory.TYPE, item));
            });
  }
}
