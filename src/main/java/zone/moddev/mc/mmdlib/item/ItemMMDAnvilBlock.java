package zone.moddev.mc.mmdlib.item;

import zone.moddev.mc.mmdlib.data.Names;
import zone.moddev.mc.mmdlib.material.IMMDObject;
import zone.moddev.mc.mmdlib.material.MMDMaterial;

public class ItemMMDAnvilBlock extends net.minecraft.item.ItemMultiTexture implements IMMDObject {

	private final MMDMaterial material;

	public ItemMMDAnvilBlock(final MMDMaterial material) {
		super(material.getBlock(Names.ANVIL), material.getBlock(Names.ANVIL),
				new String[] { "intact", "slightlyDamaged", "veryDamaged" });
		this.material = material;
	}

	@Override
	public MMDMaterial getMMDMaterial() {
		return this.material;
	}

	@Override
	public int getMetadata(final int damage) {
		return damage << 2;
	}
}
