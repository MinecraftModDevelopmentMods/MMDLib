package zone.moddev.mc.mmdlib.util;

import zone.moddev.mc.mmdlib.init.MMDCreativeTab;

public final class TabContainer {

	public final MMDCreativeTab blocksTab;
	public final MMDCreativeTab itemsTab;
	public final MMDCreativeTab toolsTab;

	public TabContainer(final MMDCreativeTab blocksTab, final MMDCreativeTab itemsTab, final MMDCreativeTab toolsTab) {
		this.blocksTab = blocksTab;
		this.itemsTab = itemsTab;
		this.toolsTab = toolsTab;
	}
}
