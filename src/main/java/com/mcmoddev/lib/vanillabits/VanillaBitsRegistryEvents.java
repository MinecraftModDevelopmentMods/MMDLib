package com.mcmoddev.lib.vanillabits;

import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.init.Materials;
import com.mcmoddev.lib.material.MMDMaterial;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid=MMDLib.MODID)
public class VanillaBitsRegistryEvents {

	public VanillaBitsRegistryEvents() {
		// TODO Auto-generated constructor stub
	}

	@SubscribeEvent
	public static void registerBlocks(RegistryEvent.Register<Block> ev) {
		Materials.getAllMaterials().stream()
		.filter(MMDMaterial::isVanilla)
		.forEach(mat -> mat.getBlocks().stream()
				.filter(bl -> "mmdlib".equals(bl.getRegistryName().getNamespace()))
				.forEach(bl -> {
					MMDLib.logger.fatal("Registering block {} ({})", bl.getRegistryName(), bl);
					ev.getRegistry().register(bl);
				}));
	}
	
	@SubscribeEvent
	public static void registerItems(RegistryEvent.Register<Item> ev) {
		Materials.getMaterialsByMod(MMDLib.MODID).stream()
		.filter(mat -> mat.isVanilla())
		.forEach(mat -> mat.getItems().stream()
				.map(it -> it.getItem())
				.filter(it -> MMDLib.MODID.equals(it.getRegistryName().getNamespace()))
				.forEach(it -> ev.getRegistry().register(it)));
		Materials.DEFAULT.getItems().stream()
		.map(it -> it.getItem())
		.filter(it -> "mmdlib".contentEquals(it.getRegistryName().getNamespace()))
		.forEach(it -> {
			MMDLib.logger.fatal("Registering {} ({})", it.getRegistryName(), it);
			ev.getRegistry().register(it);
		});
	}
	
	@SubscribeEvent
	public static void registerRecipes(RegistryEvent.Register<IRecipe> ev) {
		// do nothing, as we have nothing to do
	}
}
