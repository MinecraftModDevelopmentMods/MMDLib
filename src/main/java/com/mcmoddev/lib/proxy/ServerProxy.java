package com.mcmoddev.lib.proxy;

import com.mcmoddev.lib.network.MMDMessages;

import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Base Metals Server Proxy
 *
 * @author Jasmine Iwanek
 *
 */
public class ServerProxy extends CommonProxy {
	@Override
	public void preInit(FMLPreInitializationEvent event) {
		super.preInit(event);
		MMDMessages.server_init();
	}
}
