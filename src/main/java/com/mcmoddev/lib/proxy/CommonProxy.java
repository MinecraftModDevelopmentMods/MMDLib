package com.mcmoddev.lib.proxy;

import java.util.HashSet;

import com.mcmoddev.lib.data.Names;
import com.mcmoddev.lib.integration.IntegrationManager;
import com.mcmoddev.lib.material.MMDMaterial;
import com.mcmoddev.lib.oregen.FallbackGenerator;
import com.mcmoddev.lib.oregen.FallbackGeneratorData;
import com.mcmoddev.lib.util.ConfigBase.Options;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.MissingModsException;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;
import net.minecraftforge.fml.common.versioning.DefaultArtifactVersion;

/**
 * Base Metals Common Proxy
 *
 * @author Jasmine Iwanek
 *
 */
public class CommonProxy {
	
	public void preInit(FMLPreInitializationEvent event) {
	    // despite other comments, most events get fired here
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
