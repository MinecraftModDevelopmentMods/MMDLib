package zone.moddev.mc.lib.client.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.api.MaterialEventTypes;
import zone.moddev.mc.lib.common.recipes.CrushingRecipe;
import zone.moddev.mc.lib.common.runtime.CrackhammerEvents;
import zone.moddev.mc.lib.common.runtime.ItemEventBridge;
import java.util.List;

/**
 * Optional JEI integration. Only JEI loads this class (via {@link JeiPlugin}); nothing else in MMDLib
 * references it, so MMDLib runs without JEI installed.
 */
@JeiPlugin
public final class MMDLibJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.parse("mmdlib:jei"); }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new CrushingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        registration.addRecipes(CrushingCategory.TYPE, level == null ? List.of()
                : level.getRecipeManager().getAllRecipesFor(CrushingRecipe.TYPE.get()));
    }

    /** Catalysts are found by binding, not class: any item bound to a crackhammer handler, addon items included. */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        var materials = MMDLib.itemMaterials();
        MMDLib.itemDefinitions().forEach((id, definition) -> {
            var material = materials.get(id);
            boolean crackhammer = CrackhammerEvents.CRUSH.equals(ItemEventBridge.binding(definition.events(), material,
                    MaterialEventTypes.TOOL_RIGHT_CLICK.name()))
                    || CrackhammerEvents.HARVEST.equals(ItemEventBridge.binding(definition.events(), material,
                    MaterialEventTypes.TOOL_HARVEST.name()));
            if (crackhammer) BuiltInRegistries.ITEM.getOptional(id)
                    .ifPresent(item -> registration.addRecipeCatalysts(CrushingCategory.TYPE, item));
        });
    }
}
