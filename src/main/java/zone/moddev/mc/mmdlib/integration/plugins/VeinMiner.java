package zone.moddev.mc.mmdlib.integration.plugins;

import javax.annotation.Nonnull;

import zone.moddev.mc.mmdlib.data.Names;
import zone.moddev.mc.mmdlib.integration.IIntegration;
import zone.moddev.mc.mmdlib.material.MMDMaterial;
import zone.moddev.mc.mmdlib.util.Config.Options;

import net.minecraftforge.fml.common.Loader;
import portablejim.veinminer.api.IMCMessage;

/**
 * VeinMiner Integration Plugin.
 *
 * @author Jasmine Iwanek
 *
 */
public class VeinMiner implements IIntegration {

	public static final String PLUGIN_MODID = "veinminer";

	@Override
	public void init() {
		if (!Options.isModEnabled(PLUGIN_MODID)) {
			return;
		}
	}

	/**
	 * Add tools to the VeinMiner list from a Material.
	 *
	 * @param material
	 *            Material to add
	 */
	protected static void addToolsForMaterial(@Nonnull final MMDMaterial material) {
		addToolsForMaterial(material.getName());
	}

	/**
	 * Add tools to the VeinMiner list from a Material.
	 *
	 * @param materialName
	 *            Name of Material to add
	 */
	protected static void addToolsForMaterial(@Nonnull final String materialName) {
		final String ownerModID = Loader.instance().activeModContainer().getModId();
		final String axe = Names.AXE.toString();
		final String hoe = Names.HOE.toString();
		final String pickaxe = Names.PICKAXE.toString();
		final String shears = Names.SHEARS.toString();
		final String shovel = Names.SHOVEL.toString();

		final String hammer = "hammer";
		IMCMessage.addTool(axe, ownerModID + ":" + materialName + "_" + axe);
		IMCMessage.addTool(hoe, ownerModID + ":" + materialName + "_" + hoe);
		IMCMessage.addTool(pickaxe, ownerModID + ":" + materialName + "_" + pickaxe);
		IMCMessage.addTool(shears, ownerModID + ":" + materialName + "_" + shears);
		IMCMessage.addTool(shovel, ownerModID + ":" + materialName + "_" + shovel);

		IMCMessage.addTool(hammer, ownerModID + ":" + materialName + "_" + hammer);
	}
}
