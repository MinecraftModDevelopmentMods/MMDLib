package com.mcmoddev.lib.events;

import com.mcmoddev.lib.init.Materials;
import com.mcmoddev.lib.API.IMMDEvent;
import com.mcmoddev.lib.API.IFluidApi;

import net.minecraftforge.fml.common.eventhandler.Event;

public class FluidRegisterEvent extends Event implements IMMDEvent<IFluidApi> {
	private final IFluidApi api;
	private final Materials registry;
	
	public FluidRegisterEvent( IFluidApi fluidAPI, Materials materialRegistry ) {
		api = fluidAPI;
		registry = materialRegistry;
	}

	@Override
	public IFluidApi getApi() {
		return api;
	}

	@Override
	public Materials getMaterialRegistry() {
		return registry;
	}
}
