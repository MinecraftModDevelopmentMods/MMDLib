package com.mcmoddev.lib.API;

import com.mcmoddev.lib.material.MMDMaterial;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public interface IItemApi extends IMMDApi {
	default String getApiName() {
		return "MMD Item->Material registration API";
	}
	
	Item createItem( String typeName, MMDMaterial material );
	ItemStack createItem( String typeName, MMDMaterial material, int metadata );
	ItemStack getItem( String typeName, MMDMaterial material );
	ItemStack getItem( String typeName, MMDMaterial material, int metadata );
	
	IItemApi addItem( String typeName, MMDMaterial material );
	IItemApi addItem( String typeName, ItemStack itemInstnace, MMDMaterial material );
	IItemApi addItem( String typeName, Item itemInstance, MMDMaterial material );
}
