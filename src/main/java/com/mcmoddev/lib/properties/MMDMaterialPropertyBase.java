package com.mcmoddev.lib.properties;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.registries.IForgeRegistryEntry;

public abstract class MMDMaterialPropertyBase extends IForgeRegistryEntry.Impl<MMDMaterialPropertyBase> implements IMMDMaterialProperty {

	public MMDMaterialPropertyBase() {
	}

	@Override
	public boolean hasEffect(ItemStack stack) {
		return false;
	}

	@Override
	public boolean hasEffect(ItemStack stack, EntityPlayer player) {
		return false;
	}

	@Override
	public boolean hasEffect(ItemStack stack, EntityLivingBase ent) {
		return false;
	}

	@Override
	public void apply(ItemStack stack) {
		if(hasEffect(stack)) {
			// only apply if there is an effect
			// we should always double check here :)
		}
	}

	@Override
	public void apply(ItemStack stack, EntityPlayer player) {
		if(hasEffect(stack, player)) {
			// only apply if there is an effect
			// we should always double check here :)
		}
	}

	@Override
	public void apply(ItemStack stack, EntityLivingBase ent) {
		if(hasEffect(stack, ent)) {
			// only apply if there is an effect
			// we should always double check here :)
		}
	}
}
