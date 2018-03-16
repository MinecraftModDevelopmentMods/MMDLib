package com.mcmoddev.lib.proxy;

import com.mcmoddev.lib.capability.MMDCapabilities;
import com.mcmoddev.lib.container.MMDGuiHandler;
import com.mcmoddev.lib.data.Names;
import com.mcmoddev.lib.init.Materials;
import com.mcmoddev.lib.material.MMDMaterial;
import com.mcmoddev.lib.network.MMDMessages;
import com.mcmoddev.lib.oregen.FallbackGeneratorData;
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

		Materials.init();
	}
	
	public void init(FMLInitializationEvent event) {
		// by this point all materials should have been registered both with MMDLib and Minecraft
		// move to a separate function - potentially in FallbackGeneratorData - after the test
		for( MMDMaterial mat : Materials.getAllMaterials() ) {
			if( mat.hasBlock(Names.ORE) ){
				FallbackGeneratorData.getInstance().addMaterial(mat.getName(), Names.ORE.toString(), mat.getDefaultDimension());
				
				if( mat.hasBlock(Names.NETHERORE) )
					FallbackGeneratorData.getInstance().addMaterial(mat.getName(), Names.NETHERORE.toString(), -1);
				
				if( mat.hasBlock(Names.ENDORE) )
					FallbackGeneratorData.getInstance().addMaterial(mat.getName(), Names.ENDORE.toString(), 1);
			}
		}
	}

	public void postInit(FMLPostInitializationEvent event) {
	}
}
