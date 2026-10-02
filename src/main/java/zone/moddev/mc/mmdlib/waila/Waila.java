package zone.moddev.mc.mmdlib.waila;

import zone.moddev.mc.mmdlib.block.BlockMMDLever;

import mcp.mobius.waila.api.IWailaRegistrar;
import net.minecraftforge.fml.common.event.FMLInterModComms;

public final class Waila {

	private Waila() {

	}

	public static void init() {
		FMLInterModComms.sendMessage("waila", "register", "zone.moddev.mc.mmdlib.waila.Waila.register");
	}

	public static void register(final IWailaRegistrar registrar) {
		registrar.registerBodyProvider(new LeverInfoController(), BlockMMDLever.class);
	}
}
