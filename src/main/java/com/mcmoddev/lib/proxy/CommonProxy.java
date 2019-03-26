package com.mcmoddev.lib.proxy;

import com.mcmoddev.lib.capability.MMDCapabilities;
import com.mcmoddev.lib.container.MMDGuiHandler;
import com.mcmoddev.lib.data.Names;
import com.mcmoddev.lib.data.VanillaMaterialNames;
import com.mcmoddev.lib.events.MMDLibRegisterBlocks;
import com.mcmoddev.lib.events.MMDLibRegisterFluids;
import com.mcmoddev.lib.events.MMDLibRegisterItems;
import com.mcmoddev.lib.events.MMDLibRegisterMaterialProperties;
import com.mcmoddev.lib.events.MMDLibRegisterMaterials;
import com.mcmoddev.lib.events.MMLibPreInitSync;
import com.mcmoddev.lib.integration.IntegrationManager;
import com.mcmoddev.lib.material.MMDMaterial;
import com.mcmoddev.lib.network.MMDMessages;
import com.mcmoddev.lib.oregen.FallbackGeneratorData;
import com.mcmoddev.lib.util.Config;
import com.mcmoddev.lib.util.MMDLibItemGroups;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Base Metals Common Proxy
 *
 * @author Jasmine Iwanek
 *
 */
public class CommonProxy {
	public void preInit(FMLPreInitializationEvent event) {
		MMDMessages.init();
		MMDGuiHandler.init();
		MMDCapabilities.init();
	    // despite other comments, most events get fired here

		
		com.mcmoddev.lib.util.MMDLibItemGroups.init();

		com.mcmoddev.lib.init.Materials.init();
		com.mcmoddev.lib.init.Blocks.init();
		com.mcmoddev.lib.init.Items.init();
		com.mcmoddev.lib.init.Fluids.init();
		com.mcmoddev.lib.init.Recipes.init();
		com.mcmoddev.lib.init.VillagerTrades.init();

		MinecraftForge.EVENT_BUS.post(new MMDLibRegisterMaterialProperties());
		MinecraftForge.EVENT_BUS.post(new MMDLibRegisterMaterials());
		MinecraftForge.EVENT_BUS.post(new MMDLibRegisterBlocks());
		MinecraftForge.EVENT_BUS.post(new MMDLibRegisterItems());
		MinecraftForge.EVENT_BUS.post(new MMDLibRegisterFluids());


		IntegrationManager.INSTANCE.preInit(event);
		MinecraftForge.EVENT_BUS.post(new MMLibPreInitSync());
		IntegrationManager.INSTANCE.preInitPhase();
	}

	/**
	 * Initialization for this mod.
	 *
	 * @param event The Event.
	 */
	public void init(final FMLInitializationEvent event) {
		// by this point all materials should have been registered both with MMDLib and Minecraft
		// move to a separate function - potentially in FallbackGeneratorData - after the test
		for (final MMDMaterial material : com.mcmoddev.lib.init.Materials.getAllMaterials()) {
			if (material.hasBlock(Names.ORE)) {
				FallbackGeneratorData.getInstance().addMaterial(material.getName(),
						Names.ORE.toString(), material.getDefaultDimension());

				if (material.hasBlock(Names.NETHERORE)) {
					FallbackGeneratorData.getInstance().addMaterial(material.getName(),
							Names.NETHERORE.toString(), -1);
				}

				if (material.hasBlock(Names.ENDORE)) {
					FallbackGeneratorData.getInstance().addMaterial(material.getName(),
							Names.ENDORE.toString(), 1);
				}
			}
		}

		MMDLibItemGroups.setupIcons(VanillaMaterialNames.IRON);  
		IntegrationManager.INSTANCE.initPhase();
	}

	/**
	 * Post Initialization for this mod.
	 *
	 * @param event The Event.
	 */
	public void postInit(final FMLPostInitializationEvent event) {
		Config.postInit();
		FallbackGeneratorData.getInstance().setup();
		IntegrationManager.INSTANCE.postInitPhase();
	}
}
