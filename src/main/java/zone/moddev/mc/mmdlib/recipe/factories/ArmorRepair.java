package zone.moddev.mc.mmdlib.recipe.factories;

import java.util.Locale;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import zone.moddev.mc.mmdlib.init.Materials;
import zone.moddev.mc.mmdlib.material.MMDMaterial;
import zone.moddev.mc.mmdlib.recipe.BootsRepairRecipe;
import zone.moddev.mc.mmdlib.recipe.ChestplateRepairRecipe;
import zone.moddev.mc.mmdlib.recipe.HelmetRepairRecipe;
import zone.moddev.mc.mmdlib.recipe.LeggingsRepairRecipe;
import zone.moddev.mc.mmdlib.recipe.ShieldRepairRecipe;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.JsonUtils;
import net.minecraftforge.common.crafting.IRecipeFactory;
import net.minecraftforge.common.crafting.JsonContext;

public class ArmorRepair implements IRecipeFactory {

	@Override
	public IRecipe parse(final JsonContext context, final JsonObject json) {
		final String material = JsonUtils.getString(json, "material");
		final String type = JsonUtils.getString(json, "armorType").toLowerCase(Locale.ROOT);
		final MMDMaterial mat = Materials.getMaterialByName(material.toLowerCase(Locale.ROOT));

		switch (type) {
			case "boots":
				return new BootsRepairRecipe(mat);
			case "leggings":
				return new LeggingsRepairRecipe(mat);
			case "chestplate":
				return new ChestplateRepairRecipe(mat);
			case "helmet":
				return new HelmetRepairRecipe(mat);
			case "shield":
				return new ShieldRepairRecipe(mat);
			default:
				throw new JsonSyntaxException("Unknown Armor Type '" + type + "' specified!");
		}
	}

}
