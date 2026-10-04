package zone.moddev.mc.mmdlib.recipe.conditions;

import java.util.function.BooleanSupplier;

import com.google.gson.JsonObject;
import zone.moddev.mc.mmdlib.util.Config.Options;

import net.minecraftforge.common.crafting.IConditionFactory;
import net.minecraftforge.common.crafting.JsonContext;

public class PlateRepairEnabled implements IConditionFactory {

	@Override
	public BooleanSupplier parse(final JsonContext context, final JsonObject json) {
		return () -> Options.enablePlateRepairs();
	}

}
