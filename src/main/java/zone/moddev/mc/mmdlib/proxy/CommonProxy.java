package zone.moddev.mc.mmdlib.proxy;

import zone.moddev.mc.mmdlib.capability.MMDCapabilities;
import zone.moddev.mc.mmdlib.container.MMDGuiHandler;
import zone.moddev.mc.mmdlib.data.Names;
import zone.moddev.mc.mmdlib.data.MaterialNames;
import zone.moddev.mc.mmdlib.events.MMDLibRegisterBlockTypes;
import zone.moddev.mc.mmdlib.events.MMDLibRegisterBlocks;
import zone.moddev.mc.mmdlib.events.MMDLibRegisterFluids;
import zone.moddev.mc.mmdlib.events.MMDLibRegisterItemTypes;
import zone.moddev.mc.mmdlib.events.MMDLibRegisterItems;
import zone.moddev.mc.mmdlib.events.MMDLibRegisterMaterialProperties;
import zone.moddev.mc.mmdlib.events.MMDLibRegisterMaterials;
import zone.moddev.mc.mmdlib.events.MMLibPreInitSync;
import zone.moddev.mc.mmdlib.integration.IntegrationManager;
import zone.moddev.mc.mmdlib.material.MMDMaterial;

import zone.moddev.mc.mmdlib.oregen.FallbackGeneratorData;
import zone.moddev.mc.mmdlib.util.Config;
import zone.moddev.mc.mmdlib.util.MMDLibItemGroups;

import net.minecraftforge.common.MinecraftForge;
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

		MMDGuiHandler.init();
		MMDCapabilities.init();
	    // Dispatch registration events in the order required by library consumers.

		zone.moddev.mc.mmdlib.util.MMDLibItemGroups.init();

		zone.moddev.mc.mmdlib.init.Materials.init();

		zone.moddev.mc.mmdlib.init.Items.init();
		zone.moddev.mc.mmdlib.init.Fluids.init();
		zone.moddev.mc.mmdlib.init.VillagerTrades.init();

		MinecraftForge.EVENT_BUS.post(new MMDLibRegisterBlockTypes());
		MinecraftForge.EVENT_BUS.post(new MMDLibRegisterItemTypes());
		zone.moddev.mc.mmdlib.init.Items.addToMetList();
		MinecraftForge.EVENT_BUS.post(new MMDLibRegisterMaterialProperties());
		MinecraftForge.EVENT_BUS.post(new MMDLibRegisterMaterials());
		MinecraftForge.EVENT_BUS.post(new MMDLibRegisterBlocks());
		MinecraftForge.EVENT_BUS.post(new MMDLibRegisterItems());
		MinecraftForge.EVENT_BUS.post(new MMDLibRegisterFluids());
		zone.moddev.mc.mmdlib.init.Recipes.init();

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

		for (final MMDMaterial material : zone.moddev.mc.mmdlib.init.Materials.getAllMaterials()) {
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

		MMDLibItemGroups.setupIcons(MaterialNames.IRON);  
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
