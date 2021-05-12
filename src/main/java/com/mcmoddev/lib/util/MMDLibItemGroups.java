package com.mcmoddev.lib.util;

import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.data.Names;
import com.mcmoddev.lib.data.SharedStrings;
import com.mcmoddev.lib.init.ItemGroups;
import com.mcmoddev.lib.init.Materials;
import com.mcmoddev.lib.material.MMDMaterial;

public class MMDLibItemGroups extends ItemGroups {

	public MMDLibItemGroups() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * Sets up icons for a CreativeTab.
	 *
	 * @param materialName
	 *            Name of the preferred Material to use for Tab Icons
	 */
	public static void setupIcons(final String materialName) {
		if (Materials.hasMaterial(materialName)) {
			final MMDMaterial material = Materials.getMaterialByName(materialName);

			if (material.hasBlock(Names.BLOCK)) {
				getTab(MMDLib.MODID, SharedStrings.TAB_BLOCKS).setTabIconItem(material.getBlock(Names.BLOCK));
			}

			if (material.hasItem(Names.GEAR)) {
				getTab(MMDLib.MODID, SharedStrings.TAB_ITEMS).setTabIconItem(material.getItem(Names.GEAR));
			}

			if (material.hasItem(Names.PICKAXE)) {
				getTab(MMDLib.MODID, SharedStrings.TAB_TOOLS).setTabIconItem(material.getItem(Names.PICKAXE));
			}

			if (material.hasItem(Names.SWORD)) {
				getTab(MMDLib.MODID, SharedStrings.TAB_COMBAT).setTabIconItem(material.getItem(Names.SWORD));
			}
		}
	}
}
