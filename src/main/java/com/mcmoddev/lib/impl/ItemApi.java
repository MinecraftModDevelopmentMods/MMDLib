package com.mcmoddev.lib.impl;

import com.mcmoddev.lib.API.IItemApi;
import com.mcmoddev.lib.material.MMDMaterial;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class ItemApi implements IItemApi {

	public static final IItemApi instance = new ItemApi();

	@Override
	public String getApiName() {
		return "MMDLib:ItemApi";
	}

	@Override
	public Item createItem(String typeName, MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ItemStack createItem(String typeName, MMDMaterial material, int metadata) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ItemStack getItem(String typeName, MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ItemStack getItem(String typeName, MMDMaterial material, int metadata) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IItemApi addItem(String typeName, MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IItemApi addItem(String typeName, ItemStack itemInstnace, MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IItemApi addItem(String typeName, Item itemInstance, MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}

}
