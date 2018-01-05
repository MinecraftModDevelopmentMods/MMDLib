package com.mcmoddev.lib.events;

import com.mcmoddev.lib.API.IMMDEvent;
import com.mcmoddev.lib.API.IBlockApi;
import com.mcmoddev.lib.init.Materials;

import net.minecraftforge.fml.common.eventhandler.Event;

public class BlockRegisterEvent extends Event implements IMMDEvent<IBlockApi> {

	private final IBlockApi api;
	private final Materials registry;

	public BlockRegisterEvent(IBlockApi blockAPI, Materials materialRegistry) {
		api = blockAPI;
		registry = materialRegistry;
	}

	@Override
	public IBlockApi getApi() {
		return api;
	}

	@Override
	public Materials getMaterialRegistry() {
		return registry;
	}
}
