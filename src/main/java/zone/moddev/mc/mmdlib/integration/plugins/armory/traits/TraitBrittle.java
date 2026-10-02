package zone.moddev.mc.mmdlib.integration.plugins.armory.traits;

import c4.conarm.lib.traits.AbstractArmorTrait;
import net.minecraft.util.text.TextFormatting;

/** Registers the brittle armor trait identifier; no armor callbacks are installed here. */
public class TraitBrittle extends AbstractArmorTrait {

	public TraitBrittle() {
		super("mmd-brittle", TextFormatting.RED);
	}

}
