package zone.moddev.mc.mmdlib.item;
import java.util.List;

import zone.moddev.mc.mmdlib.data.MaterialStats;
import zone.moddev.mc.mmdlib.data.Names;
import zone.moddev.mc.mmdlib.material.IMMDObject;
import zone.moddev.mc.mmdlib.material.MMDMaterial;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

import net.minecraft.world.World;

/**
 *
 * @author Jasmine Iwanek
 *
 */

public class ItemMMDShield extends net.minecraft.item.ItemShield implements IMMDObject {

	private final MMDMaterial material;

	/**
	 *
	 * @param material
	 *            The material to make the shield from
	 */
	public ItemMMDShield(final MMDMaterial material) {
		this.material = material;
		this.setMaxDamage((int) (this.material.getStat(MaterialStats.STRENGTH) * 168));
	}

	@Override
	public void onUpdate(final ItemStack item, final World world, final Entity player,
			final int inventoryIndex, final boolean isHeld) {
		MMDItemHelper.doRegeneration(item, world, isHeld, this.material.regenerates());
	}

	@Override
	public MMDMaterial getMMDMaterial() {
		return this.material;
	}

	/**
	 * Return whether this item is repairable in an anvil.
	 */
	@Override
	public boolean getIsRepairable(final ItemStack toRepair, final ItemStack repairMaterial) {
		return MMDItemHelper.isToolRepairable(repairMaterial, this.material.getCapitalizedName());
	}

	@Override
	public void addInformation(final ItemStack stack, final World worldIn,
			final List<String> tooltip, final ITooltipFlag flagIn) {
		List<String> tt = this.getMMDMaterial().getTooltipFor(Names.SHIELD);
		if(!tt.isEmpty())
			tooltip.addAll(tt);
	}

}
