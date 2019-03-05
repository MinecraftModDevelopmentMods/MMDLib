package com.mcmoddev.lib.init;

import com.mcmoddev.lib.events.MMDLibRegisterProperty;
import com.mcmoddev.lib.properties.MMDMaterialPropertyBase;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;

public class MaterialProperties {
	private static final IForgeRegistry<MMDMaterialPropertyBase> registry = new RegistryBuilder<MMDMaterialPropertyBase>()
			.allowModification().setIDRange(0, 8192).setName(new ResourceLocation("mmdlib", "material_properties"))
			.setMaxID(8192).setType(MMDMaterialPropertyBase.class).create();
	
	public MaterialProperties() {
		// TODO Auto-generated constructor stub
	}

	public void init() {
		MinecraftForge.EVENT_BUS.post(new MMDLibRegisterProperty(registry));
	}
}
