package com.mcmoddev.lib.item;

import java.util.List;

import com.mcmoddev.lib.data.Names;
//import com.mcmoddev.basemetals.items.MMDToolEffects;
import com.mcmoddev.lib.init.Materials;
import com.mcmoddev.lib.material.IMMDObject;
import com.mcmoddev.lib.material.MMDMaterial;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Shovels.
 *
 * @author DrCyano
 *
 */
public class ItemMMDShovel extends net.minecraft.item.ItemSpade implements IMMDObject {

	private final MMDMaterial material;

	/**
	 *
	 * @param material
	 *            The material to make the shovel from
	 */
	public ItemMMDShovel(final MMDMaterial material) {
		super(Materials.getToolMaterialFor(material));
		this.material = material;
		this.setMaxDamage(this.material.getToolDurability());
		this.efficiency = this.material.getToolEfficiency();
	}

	@Override
	public int getItemEnchantability() {
		return this.toolMaterial.getEnchantability();
	}

	@Override
	public String getToolMaterialName() {
		return this.toolMaterial.toString();
	}

	@Override
	public boolean getIsRepairable(final ItemStack intputItem, final ItemStack repairMaterial) {
		return MMDItemHelper.isToolRepairable(repairMaterial, this.material.getCapitalizedName());
	}

	@Override
	public boolean hitEntity(final ItemStack item, final EntityLivingBase target,
			final EntityLivingBase attacker) {
		super.hitEntity(item, target, attacker);
		if(this.getMMDMaterial().hasEffect(item, target)) {
			this.getMMDMaterial().applyEffect(item, target);
		}
		return true;
	}

	@Override
	public void onCreated(final ItemStack item, final World world, final EntityPlayer crafter) {
		super.onCreated(item, world, crafter);
		if(this.getMMDMaterial().hasEffect(item, crafter)) {
			this.getMMDMaterial().applyEffect(item, crafter);
		}
	}

	@Override
	public void onUpdate(final ItemStack item, final World world, final Entity player,
			final int inventoryIndex, final boolean isHeld) {
		MMDItemHelper.doRegeneration(item, world, isHeld, this.material.regenerates());
	}

	@Override
	public void addInformation(final ItemStack stack, final World worldIn,
			final List<String> tooltip, final ITooltipFlag flagIn) {
		List<String> tt = this.getMMDMaterial().getTooltipFor(Names.SHOVEL);
		if(!tt.isEmpty())
			tooltip.addAll(tt);
	}

	@Override
	public MMDMaterial getMMDMaterial() {
		return this.material;
	}
}
