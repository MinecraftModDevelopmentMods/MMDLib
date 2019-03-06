package com.mcmoddev.lib.events;

import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.IContextSetter;
import net.minecraftforge.registries.IForgeRegistry;

import com.mcmoddev.lib.properties.MMDMaterialPropertyBase;
import com.mcmoddev.lib.properties.MaterialProperties;

public class MMDLibRegisterMaterialProperties extends Event implements IContextSetter {
	private final IForgeRegistry<MMDMaterialPropertyBase> reg;
	
	public MMDLibRegisterMaterialProperties() {
		this.reg = MaterialProperties.get();
	}

	public IForgeRegistry<MMDMaterialPropertyBase> getRegistry() {
		return this.reg;
	}
}
