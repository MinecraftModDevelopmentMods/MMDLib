package com.mcmoddev.lib;

import com.mcmoddev.lib.proxy.CommonProxy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * This is the entry point for this Mod. If you are writing your own Mod that
 * uses this Mod, the classes of interest to you are the init classes (classes
 * in package com.mcmoddev.basemetals.init) and the CrusherRecipeRegistry class
 * (in package com.mcmoddev.basemetals.registry). Note that you should add
 * 'dependencies = "required-after:basemetals"' to your &#64;Mod annotation
 * (e.g. <br>
 * &#64;Mod(modid = "moremetals", name="More Metals!", version = "1.2.3",
 * dependencies = "required-after:basemetals") <br>
 * )
 *
 * @author Jasmine Iwanek
 *
 */
@Mod(
		modid = MMDLib.MODID,
		name = MMDLib.NAME,
		version = MMDLib.VERSION,
		dependencies = "required-after:forge@[14.21.0.2327,);after:tconstruct;after:ic2;before:buildingbricks",
		acceptedMinecraftVersions = "[1.12,)",
		updateJSON = MMDLib.UPDATEJSON)
public class MMDLib {

	@Instance
	public static MMDLib instance;

	/** ID of this Mod */
	public static final String MODID = "mmdlib";

	/** Display name of this Mod */
	static final String NAME = "MMDLib";

	/**
	 * Version number, in Major.Minor.Patch format. The minor number is
	 * increased whenever a change is made that has the potential to break
	 * compatibility with other mods that depend on this one.
	 */
	public static final String VERSION = "1.0.0-beta1";

	static final String UPDATEJSON = "https://raw.githubusercontent.com/MinecraftModDevelopment/MMDLib/master/update.json";

	private static final String PROXY_BASE = "com.mcmoddev.lib.proxy.";

	@SidedProxy(clientSide = PROXY_BASE + "ClientProxy", serverSide = PROXY_BASE + "ServerProxy")
	public static CommonProxy proxy;

	public static final Logger logger = LogManager.getFormatterLogger(MMDLib.MODID);

	static {
		// Forge says this needs to be statically initialized here.
		FluidRegistry.enableUniversalBucket();
	}

	@EventHandler
	public static void preInit(FMLPreInitializationEvent event) {
		proxy.preInit(event);
		/*
		 * Pull the lever, Kronk!
		 */

		// TODO: this should probably not be here
//		new TinkersConstructBase().init();
	}

	@EventHandler
	public static void init(FMLInitializationEvent event) {
		proxy.init(event);
		// if we have anything else to do here, check 'proxy.allsGood' first
	}

	@EventHandler
	public static void postInit(FMLPostInitializationEvent event) {
		proxy.postInit(event);
		// if we have anything else to do here, check 'proxy.allsGood' first
	}
}
