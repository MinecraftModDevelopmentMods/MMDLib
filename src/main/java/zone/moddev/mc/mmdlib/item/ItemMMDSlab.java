package zone.moddev.mc.mmdlib.item;

import zone.moddev.mc.mmdlib.data.Names;
import zone.moddev.mc.mmdlib.material.IMMDObject;
import zone.moddev.mc.mmdlib.material.MMDMaterial;

import net.minecraft.block.BlockSlab;

/**
 *
 * @author Jasmine Iwanek
 *
 */
public class ItemMMDSlab extends net.minecraft.item.ItemSlab implements IMMDObject {

	private final MMDMaterial material;

	/**
	 *
	 * @param material
	 *            The material to make the slab from
	 */
	public ItemMMDSlab(final MMDMaterial material) {
		super(material.getBlock(Names.SLAB), (BlockSlab) material.getBlock(Names.SLAB),
				(BlockSlab) material.getBlock(Names.DOUBLE_SLAB));
		this.material = material;
		this.setMaxDamage(0);
		this.setHasSubtypes(false);
	}

	@Override
	public MMDMaterial getMMDMaterial() {
		return this.material;
	}
}
