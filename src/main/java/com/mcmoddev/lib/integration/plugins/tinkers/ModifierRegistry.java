package com.mcmoddev.lib.integration.plugins.tinkers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.integration.plugins.tinkers.modifiers.ModifierFakeDiamond;
import com.mcmoddev.lib.integration.plugins.tinkers.modifiers.ModifierLeadPlated;
import com.mcmoddev.lib.integration.plugins.tinkers.modifiers.ModifierToxic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import slimeknights.mantle.util.RecipeMatch;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * Contains static instances of modifiers for registering with TiC.
 *
 * @author Daniel Hazelton &lt;dshadowwolf@gmail.com&gt;
 *
 */

public class ModifierRegistry {

	private static final Map<String, Modifier> modifiers = new HashMap<>();

	private ModifierRegistry() {
		throw new IllegalAccessError("Not an instantiable class");
	}

	/**
	 *
	 * @param name
	 * @param ingredients
	 */
	public static void setModifierRecipe(@Nonnull final String name, @Nonnull final ItemStack... ingredients) {
		final Modifier t = modifiers.get(name);
		if (t == null) {
			MMDLib.logger.error("Trying to add a recipe to unknown modifier %s, ignoring.", name);
			return;
		}

		t.addRecipeMatch(new RecipeMatch.ItemCombination(1, ingredients));
		modifiers.put(name, t);
	}

	public static void setModifierItem(@Nonnull final String name, @Nonnull final ItemStack item) {
		setModifierItem(name, item.getItem());
	}

	/**
	 *
	 * @param name
	 * @param item
	 */
	public static void setModifierItem(@Nonnull final String name, @Nonnull final Item item) {
		final Modifier t = modifiers.get(name);
		if (t == null) {
			MMDLib.logger.error("Trying to add an item to unknown modifier %s, ignoring.", name);
			return;
		}

		t.addItem(item);
		((Modifier) TinkerRegistry.getModifier(t.getIdentifier())).addItem(item);
		modifiers.put(name, t);
	}

	/**
	 *
	 */
	public static void initModifiers() {
		modifiers.put("toxic", new ModifierToxic());
		modifiers.put("plated", new ModifierLeadPlated());
		modifiers.put("fake-diamond", new ModifierFakeDiamond());
	}

	public static void registerModifiers() {
		// blank on purpose - this is a no-op with 2.7.+ and the entire 2.8 and 2.9 series
	}

	/**
	 *
	 * @param name
	 * @return
	 */
	public static Map<String, String> getModifierDetails(@Nonnull final String name) {
		final Map<String, String> rv = new HashMap<>();
		if (modifiers.containsKey(name)) {
			Modifier t = modifiers.get(name);
			rv.put("name", t.getLocalizedName());
			rv.put("desc", t.getLocalizedDesc());
			return rv;
		} else {
			return Collections.emptyMap();
		}
	}
}
