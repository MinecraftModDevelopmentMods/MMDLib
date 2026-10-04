package zone.moddev.mc.mmdlib.item;

import zone.moddev.mc.mmdlib.data.Names;
import zone.moddev.mc.mmdlib.material.IMMDObject;
import zone.moddev.mc.mmdlib.material.MMDMaterial;
import zone.moddev.mc.mmdlib.material.MMDMaterialType.MaterialType;

import net.minecraft.block.BlockDoor;
import net.minecraft.item.ItemStack;

/**
 * Doors.
 */
public class ItemMMDDoor extends net.minecraft.item.ItemDoor implements IMMDObject {

	private final MMDMaterial mmdMaterial;

	/**
	 *
	 * @param material
	 *            The material to make the door from
	 */
	public ItemMMDDoor(final MMDMaterial material) {
		super(material.getBlock(Names.DOOR));
		this.mmdMaterial = material;
	}

	/**
	 *
	 * @param block
	 *            The block to use to make the door
	 * @param material
	 *            The material to make the door from
	 */
	public ItemMMDDoor(final BlockDoor block, final MMDMaterial material) {
		super(block);
		this.mmdMaterial = material;
	}

	@Override
    public int getItemBurnTime(final ItemStack itemStack)
    {
    	if (this.mmdMaterial.getType() == MaterialType.WOOD) {
    		return -1;
    	} else {
    		return 0;
    	}
    }

	@Override
	public MMDMaterial getMMDMaterial() {
		return this.mmdMaterial;
	}
}
