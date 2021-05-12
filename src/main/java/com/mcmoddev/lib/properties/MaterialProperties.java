package com.mcmoddev.lib.properties;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;

public class MaterialProperties {
	private static final IForgeRegistry<MMDMaterialPropertyBase> registry = new RegistryBuilder<MMDMaterialPropertyBase>()
			.allowModification().setIDRange(0, 8192).setName(new ResourceLocation("mmdlib", "material_properties"))
			.setMaxID(8192).setType(MMDMaterialPropertyBase.class).create();

	public static IForgeRegistry<MMDMaterialPropertyBase> get() {
		return registry;
	}
}
