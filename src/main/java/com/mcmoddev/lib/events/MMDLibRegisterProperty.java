package com.mcmoddev.lib.events;

import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.IContextSetter;
import net.minecraftforge.registries.IForgeRegistry;

import com.mcmoddev.lib.properties.MMDMaterialPropertyBase;

public class MMDLibRegisterProperty extends Event implements IContextSetter {
	private final IForgeRegistry<MMDMaterialPropertyBase> reg;
	
	public MMDLibRegisterProperty(IForgeRegistry<MMDMaterialPropertyBase> reg) {
		this.reg = reg;
	}

	public IForgeRegistry<MMDMaterialPropertyBase> getRegistry() {
		return this.reg;
	}
}
