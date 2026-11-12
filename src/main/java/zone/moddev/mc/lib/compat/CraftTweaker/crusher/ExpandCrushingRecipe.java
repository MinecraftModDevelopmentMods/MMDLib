package zone.moddev.mc.lib.compat.CraftTweaker.crusher;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;
import com.blamejared.crafttweaker_annotations.annotations.NativeTypeRegistration;

import zone.moddev.mc.lib.common.recipes.CrushingRecipe;

/**
 * A crushing recipe, as the crushing recipe manager's queries return it.
 */
@ZenRegister
@Document("mods/MMDLib/crusher/CrushingRecipe")
@NativeTypeRegistration(value = CrushingRecipe.class, zenCodeName = "mods.mmdlib.crusher.CrushingRecipe")
public final class ExpandCrushingRecipe {
}
