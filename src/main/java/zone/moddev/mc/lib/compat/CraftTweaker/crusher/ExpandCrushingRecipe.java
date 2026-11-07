package zone.moddev.mc.lib.compat.CraftTweaker.crusher;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.NativeTypeRegistration;
import zone.moddev.mc.lib.common.recipes.CrushingRecipe;

/** Expose the native recipe type for inherited recipe-manager query results. */
@ZenRegister
@NativeTypeRegistration(value = CrushingRecipe.class, zenCodeName = "mods.mmdlib.crusher.CrushingRecipe")
public final class ExpandCrushingRecipe {}
