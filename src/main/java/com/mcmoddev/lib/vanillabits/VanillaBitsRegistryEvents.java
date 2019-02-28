package com.mcmoddev.lib.vanillabits;

import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.init.Materials;

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
		Materials.getMaterialsByMod(MMDLib.MODID).stream()
		.filter(mat -> !mat.isVanilla())
		.forEach(mat -> mat.getBlocks().stream().forEach(bl -> ev.getRegistry().register(bl)));
	}
	
	@SubscribeEvent
	public static void registerItems(RegistryEvent.Register<Item> ev) {
		Materials.getMaterialsByMod(MMDLib.MODID).stream()
		.filter(mat -> !mat.isVanilla())
		.forEach(mat -> mat.getItems().stream().forEach(it -> ev.getRegistry().register(it.getItem())));
	}
	
	@SubscribeEvent
	public static void registerRecipes(RegistryEvent.Register<IRecipe> ev) {
		// do nothing, as we have nothing to do
	}
}
