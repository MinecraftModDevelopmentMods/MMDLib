package com.mcmoddev.lib.events;

import com.mcmoddev.lib.init.Materials;
import com.mcmoddev.lib.API.IMMDEvent;
import com.mcmoddev.lib.API.IItemApi;

import net.minecraftforge.fml.common.eventhandler.Event;

public class ItemRegisterEvent extends Event implements IMMDEvent<IItemApi> {
	private final IItemApi api;
	private final Materials registry;
	
	public ItemRegisterEvent( IItemApi itemsAPI, Materials materialRegistry ) {
		api = itemsAPI;
		registry = materialRegistry;
	}

	@Override
	public IItemApi getApi() {
		return api;
	}

	@Override
	public Materials getMaterialRegistry() {
		return registry;
	}
}
