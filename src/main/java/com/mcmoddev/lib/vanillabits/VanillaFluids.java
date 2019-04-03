package com.mcmoddev.lib.vanillabits;

import java.util.Arrays;
import java.util.List;

import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.data.VanillaMaterialNames;
import com.mcmoddev.lib.events.MMDLibRegisterFluids;
import com.mcmoddev.lib.init.Fluids;
import com.mcmoddev.lib.util.Config.Options;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid=MMDLib.MODID)
public class VanillaFluids extends Fluids {
	@SubscribeEvent(priority=EventPriority.HIGHEST)
	public static void registerFluids(MMDLibRegisterFluids ev) {
		// Vanilla Materials need to always have fluids available in case of tie-in mods
		final List<String> vanillaMaterials = Arrays.asList(VanillaMaterialNames.CHARCOAL,
				VanillaMaterialNames.COAL, VanillaMaterialNames.DIAMOND, VanillaMaterialNames.EMERALD,
				VanillaMaterialNames.GOLD, VanillaMaterialNames.IRON, VanillaMaterialNames.OBSIDIAN,
				VanillaMaterialNames.PRISMARINE, VanillaMaterialNames.REDSTONE);

		vanillaMaterials.stream().filter(Options::isFluidEnabled).forEach(materialName -> {
			addFluid(materialName, 2000, 10000, 769, 10);
			addFluidBlock(materialName);
		});
	}
}
