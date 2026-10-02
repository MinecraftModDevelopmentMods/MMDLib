package zone.moddev.mc.mmdlib.item;

import zone.moddev.mc.mmdlib.entity.EntityCustomBolt;
import zone.moddev.mc.mmdlib.material.IMMDObject;
import zone.moddev.mc.mmdlib.material.MMDMaterial;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 *
 * @author Jasmine Iwanek
 *
 */
public class ItemMMDBolt extends ItemBolt implements IMMDObject {

	private final MMDMaterial material;

	/**
	 *
	 * @param material
	 *            The material to make the bolt from
	 */
	public ItemMMDBolt(final MMDMaterial material) {
		this.material = material;
	}

	/**
	 *
	 * @param worldIn
	 *            The world
	 * @param stack
	 *            The itemstack
	 * @param shooter
	 *            The shooter
	 * @return The Custom Bolt
	 */
	@Override
	public EntityCustomBolt createBolt(final World worldIn, final ItemStack stack,
			final EntityPlayer shooter) {
		return new EntityCustomBolt(worldIn, stack, shooter);
	}

	@Override
	public MMDMaterial getMMDMaterial() {
		return this.material;
	}
}
