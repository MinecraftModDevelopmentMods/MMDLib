package com.mcmoddev.lib.vanillabits;

import java.util.Arrays;

import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.client.renderer.FluidStateMapper;
import com.mcmoddev.lib.data.VanillaMaterialNames;
import com.mcmoddev.lib.init.Fluids;
import com.mcmoddev.lib.init.Materials;
import com.mcmoddev.lib.material.MMDMaterial;
import com.mcmoddev.lib.util.Oredicts;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid=MMDLib.MODID)
public class VanillaBitsRegistryEvents {

	@SubscribeEvent
	public static void registerBlocks(RegistryEvent.Register<Block> ev) {
		Materials.getAllMaterials().stream()
		.filter(MMDMaterial::isVanilla)
		.forEach(mat -> mat.getBlocks().stream()
				.filter(bl -> "mmdlib".equals(bl.getRegistryName().getNamespace()))
				.forEach(bl -> ev.getRegistry().register(bl)));
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
		.forEach(it -> ev.getRegistry().register(it));
		
		Oredicts.registerBlockOreDictionaryEntries();
		Oredicts.registerItemOreDictionaryEntries();
	}
	
	@SubscribeEvent
	public static void modelRegistryBits(ModelRegistryEvent ev) {
		Arrays.asList(VanillaMaterialNames.CHARCOAL, VanillaMaterialNames.COAL,
				VanillaMaterialNames.DIAMOND, VanillaMaterialNames.EMERALD, VanillaMaterialNames.ENDER, 
				VanillaMaterialNames.GOLD, VanillaMaterialNames.IRON, VanillaMaterialNames.LAPIS,
				VanillaMaterialNames.OBSIDIAN, VanillaMaterialNames.PRISMARINE, VanillaMaterialNames.QUARTZ,
				VanillaMaterialNames.WOOD, VanillaMaterialNames.REDSTONE, VanillaMaterialNames.STONE, 
				VanillaMaterialNames.REDSTONE).stream()
		.forEach(name -> {
			if(!VanillaMaterialNames.PRISMARINE.equalsIgnoreCase(name)) {
				Materials.getMaterialByName(name).getItems().stream()
				.filter(item -> MMDLib.MODID.equalsIgnoreCase(item.getItem().getRegistryName().getNamespace()))
				.filter(item -> !item.getItem().getRegistryName().getPath().equalsIgnoreCase(name))
				.forEach(item -> ModelLoader.setCustomModelResourceLocation(item.getItem(), 0,
						new ModelResourceLocation(item.getItem().getRegistryName(), "inventory")));
			}

			final Block block = Fluids.getFluidBlockByName(name);
			if(block != null) {
				final Item item = Item.getItemFromBlock(block);
				final ResourceLocation resourceLocation = block.getRegistryName();
				final FluidStateMapper mapper = new FluidStateMapper(
						resourceLocation.getNamespace() + ":" + name);

				if(MMDLib.MODID.equalsIgnoreCase(item.getRegistryName().getNamespace())) {
					if (item != null) {
						ModelBakery.registerItemVariants(item);
						ModelLoader.setCustomMeshDefinition(item, mapper);
					}
					ModelLoader.setCustomStateMapper(block, mapper);
				}
			}
		});
		

	}
    
	@SubscribeEvent
	public static void registerRecipes(RegistryEvent.Register<IRecipe> ev) {
		// do nothing, as we have nothing to do
	}
}
