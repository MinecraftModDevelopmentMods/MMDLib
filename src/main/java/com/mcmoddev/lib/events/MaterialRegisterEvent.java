/**
 * 
 */
package com.mcmoddev.lib.events;

import com.mcmoddev.lib.init.Materials;
import com.mcmoddev.lib.API.IMMDEvent;
import com.mcmoddev.lib.API.IMaterialApi;

import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * @author madman
 *
 */
public class MaterialRegisterEvent extends Event implements IMMDEvent<IMaterialApi> {

	private final IMaterialApi api;
	private final Materials registry;

	public MaterialRegisterEvent(IMaterialApi materialApi, Materials materialRegistry) {
		api = materialApi;
		registry = materialRegistry;
	}

	@Override
	public IMaterialApi getApi() {
		return api;
	}

	@Override
	public Materials getMaterialRegistry() {
		return registry;
	}
}
