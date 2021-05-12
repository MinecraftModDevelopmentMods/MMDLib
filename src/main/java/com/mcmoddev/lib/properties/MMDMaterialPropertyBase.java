package com.mcmoddev.lib.properties;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.registries.IForgeRegistryEntry;

public abstract class MMDMaterialPropertyBase extends IForgeRegistryEntry.Impl<MMDMaterialPropertyBase> implements IMMDMaterialProperty {
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
}
