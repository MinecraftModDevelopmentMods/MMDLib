package com.mcmoddev.lib.proxy;

import com.mcmoddev.lib.client.registrations.RegistrationHelper;
import com.mcmoddev.lib.data.Names;
import com.mcmoddev.lib.init.*;
import com.mcmoddev.lib.network.MMDMessages;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Base Metals Client Proxy
 *
 * @author Jasmine Iwanek
 *
 */
public class ClientProxy extends CommonProxy {

	@Override
	public void preInit(FMLPreInitializationEvent event) {
		super.preInit(event);
		MMDMessages.client_init();
		MinecraftForge.EVENT_BUS.register(this);
	}
	/**
	 * Registers Block and Item models for this mod.
	 *
	 * @param event The Event.
	 */
	@SubscribeEvent
	public static void registerModels(final ModelRegistryEvent event) {
		for (final String name : Items.getItemRegistry().keySet()) {
			if (!name.endsWith(Names.ANVIL.toString())) {
				RegistrationHelper.registerItemRender(name);
			}
		}

		for (final String name : Blocks.getBlockRegistry().keySet()) {
			RegistrationHelper.registerBlockRender(name);
		}
	
		for (final String name : Fluids.getFluidBlockRegistry().keySet()) {
			final Block block = Fluids.getFluidBlockByName(name);
			final Item item = Item.getItemFromBlock(block);
			
			final ModelResourceLocation fluidModelLocation = new ModelResourceLocation(item.getRegistryName().getNamespace() + ":" + name, "fluid");
			ModelBakery.registerItemVariants(item);
			ModelLoader.setCustomMeshDefinition(item, stack -> fluidModelLocation);
			ModelLoader.setCustomStateMapper(block, new StateMapperBase() {
				@Override
				protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
					return fluidModelLocation;
				}
			});
		}

	}

	@Override
	public void init(FMLInitializationEvent event) {
		super.init(event);
		for (final String name : Items.getItemRegistry().keySet()) {
			registerRenderOuter(Items.getItemByName(name));
		}

		for (final String name : Blocks.getBlockRegistry().keySet()) {
			registerRenderOuter(Blocks.getBlockByName(name));
		}
	}

	private void registerRenderOuter ( Item item ) {
		if (item != null) {
			registerRender(item, Items.getNameOfItem(item));
		}
	}

	private void registerRenderOuter ( Block block ) {
		if ((block instanceof BlockDoor) || (block instanceof BlockSlab)) {
			return; // do not add door blocks or slabs
		}

		if (block != null) {
			registerRender(Item.getItemFromBlock(block), Blocks.getNameOfBlock(block));
		}
	}

	public void registerRender(Item item, String name) {
		String resourceDomain = item.getRegistryName().getNamespace();
		ResourceLocation resLoc = new ResourceLocation(resourceDomain, name);
		ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(resLoc, "inventory"));
	}
}
