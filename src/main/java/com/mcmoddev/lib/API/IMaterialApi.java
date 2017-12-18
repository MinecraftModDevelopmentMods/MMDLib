package com.mcmoddev.lib.API;

import org.apache.commons.lang3.tuple.Pair;

import com.mcmoddev.lib.material.MMDMaterial;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fluids.Fluid;

public interface IMaterialApi extends IMMDApi {
	default String getApiName() {
		return "MMD Material Creation Api";
	}
	MMDMaterial createMaterial( String name, int hardness, int durability, int magicAffinity, int color, boolean glows );
	MMDMaterial createVanillaMaterial( String name, int hardness, int durability, int magicAffinity, int color, boolean glows );
	MMDMaterial createOrelessMaterial( String name, int hardness, int durability, int magicAffinity, int color, boolean glows );
	MMDMaterial createAlloyMaterial( String name, int hardness, int durability, int magicAffinity, int color, boolean glows );
	MMDMaterial createSpecialMaterial( String name, int hardness, int durability, int magicAffinity, int color, boolean glows );
	MMDMaterial createRareMaterial( String name, int hardness, int durability, int magicAffinity, int color, boolean glows );
	MMDMaterial createRareAlloyMaterial( String name, int hardness, int durability, int magicAffinity, int color, boolean glows );
	MMDMaterial createRareOrelessMaterial( String name, int hardness, int durability, int magicAffinity, int color, boolean glows );
	MMDMaterial createRareSpecialMaterial( String name, int hardness, int durability, int magicAffinity, int color, boolean glows );
	
	IMaterialApi registerItemType( String typeName, MMDMaterial material, Class<? extends Item> clazz);
	IMaterialApi registerBlockType( String typeName, MMDMaterial material, Class<? extends Block> clazz);
	IMaterialApi registerBlockType( String typeName, MMDMaterial material, Class<? extends Block> blockClass, Class<? extends ItemBlock> itemBlockClass);
	IMaterialApi registerFluidType( String typeName, MMDMaterial material, Class<? extends Fluid> fluidClass, Class<? extends BlockFluidBase> fluidBlockClass );
	
	Class<? extends Item> getItemType( String typeName, MMDMaterial material );
	Pair<Class<? extends Block>, Class<? extends ItemBlock>> getBlockType( String typeName, MMDMaterial material );
	Pair<Class<? extends Fluid>, Class<? extends BlockFluidBase>> getFluidType( String typeName, MMDMaterial material );
	
	MMDMaterial getEmptyMaterial();
}
