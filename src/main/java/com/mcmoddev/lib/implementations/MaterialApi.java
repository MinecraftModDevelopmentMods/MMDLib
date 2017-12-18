package com.mcmoddev.lib.implementations;

import org.apache.commons.lang3.tuple.Pair;

import com.mcmoddev.lib.API.IMaterialApi;
import com.mcmoddev.lib.material.MMDMaterial;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fluids.Fluid;

public class MaterialApi implements IMaterialApi {

	public static final IMaterialApi instance = new MaterialApi();

	@Override
	public String getApiName() {
		return "MMDLib::MaterialApi";
	}

	@Override
	public MMDMaterial createMaterial(String name, int hardness, int durability, int magicAffinity, int color,
			boolean glows) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MMDMaterial createVanillaMaterial(String name, int hardness, int durability, int magicAffinity, int color,
			boolean glows) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MMDMaterial createOrelessMaterial(String name, int hardness, int durability, int magicAffinity, int color,
			boolean glows) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MMDMaterial createAlloyMaterial(String name, int hardness, int durability, int magicAffinity, int color,
			boolean glows) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MMDMaterial createSpecialMaterial(String name, int hardness, int durability, int magicAffinity, int color,
			boolean glows) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MMDMaterial createRareMaterial(String name, int hardness, int durability, int magicAffinity, int color,
			boolean glows) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MMDMaterial createRareAlloyMaterial(String name, int hardness, int durability, int magicAffinity, int color,
			boolean glows) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MMDMaterial createRareOrelessMaterial(String name, int hardness, int durability, int magicAffinity,
			int color, boolean glows) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MMDMaterial createRareSpecialMaterial(String name, int hardness, int durability, int magicAffinity,
			int color, boolean glows) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IMaterialApi registerItemType(String typeName, MMDMaterial material, Class<? extends Item> clazz) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IMaterialApi registerBlockType(String typeName, MMDMaterial material, Class<? extends Block> clazz) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IMaterialApi registerBlockType(String typeName, MMDMaterial material, Class<? extends Block> blockClass,
			Class<? extends ItemBlock> itemBlockClass) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IMaterialApi registerFluidType(String typeName, MMDMaterial material, Class<? extends Fluid> fluidClass,
			Class<? extends BlockFluidBase> fluidBlockClass) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Class<? extends Item> getItemType(String typeName, MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Pair<Class<? extends Block>, Class<? extends ItemBlock>> getBlockType(String typeName,
			MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Pair<Class<? extends Fluid>, Class<? extends BlockFluidBase>> getFluidType(String typeName,
			MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MMDMaterial getEmptyMaterial() {
		// TODO Auto-generated method stub
		return null;
	}

}
