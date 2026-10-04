package zone.moddev.mc.mmdlib.recipe;

import zone.moddev.mc.mmdlib.data.Names;
import zone.moddev.mc.mmdlib.material.MMDMaterial;
import zone.moddev.mc.mmdlib.util.Oredicts;

public class ShieldRepairRecipe extends RepairRecipeBase {

	public ShieldRepairRecipe(final MMDMaterial material) {
		super(material, Names.SHIELD, Oredicts.SHIELD + material.getCapitalizedName(),
				Oredicts.PLATE + material.getCapitalizedName());
	}
}
