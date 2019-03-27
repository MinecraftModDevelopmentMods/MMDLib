package com.mcmoddev.lib.item;

import java.util.List;

import com.mcmoddev.lib.data.Names;
//import com.mcmoddev.basemetals.items.MMDToolEffects;
import com.mcmoddev.lib.init.Materials;
import com.mcmoddev.lib.material.IMMDObject;
import com.mcmoddev.lib.material.MMDMaterial;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Swords.
 *
 * @author DrCyano
 *
 */
public class ItemMMDSword extends net.minecraft.item.ItemSword implements IMMDObject {

	private final MMDMaterial material;
	private final float attackDamage;

	/**
	 *
	 * @param material
	 *            The material to make the sword from
	 */
	public ItemMMDSword(final MMDMaterial material) {
		super(Materials.getToolMaterialFor(material));
		this.material = material;
		this.setMaxDamage(this.material.getToolDurability());
		// this.damageVsEntity = attackDamage + material.getBaseAttackDamage(); // damageVsEntity is
		// private, sadly
		this.attackDamage = 3F + this.material.getBaseAttackDamage();
	}

	@Override
	public boolean onBlockDestroyed(final ItemStack item, final World world,
			final IBlockState block, final BlockPos coord, final EntityLivingBase entity) {
		if (block.getBlockHardness(world, coord) != 0.0) {
			item.damageItem(2, entity);
		}
		return true;
	}

	@Override
	public int getItemEnchantability() {
		return this.material.getEnchantability();
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

	/**
	 *
	 * @return The amount of damage dealt to an entity when attacked by this item
	 */
	@Override
	public float getAttackDamage() {
		return this.attackDamage;
	}

	@Override
	public void addInformation(final ItemStack stack, final World worldIn,
			final List<String> tooltip, final ITooltipFlag flagIn) {
		List<String> tt = this.getMMDMaterial().getTooltipFor(Names.SWORD);
		if(!tt.isEmpty())
			tooltip.addAll(tt);
	}

	@Override
	public MMDMaterial getMMDMaterial() {
		return this.material;
	}
}
