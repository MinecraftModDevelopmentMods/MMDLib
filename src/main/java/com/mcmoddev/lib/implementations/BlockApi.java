package com.mcmoddev.lib.implementations;

import org.apache.commons.lang3.tuple.Pair;

import com.mcmoddev.lib.API.IBlockApi;
import com.mcmoddev.lib.init.Materials;
import com.mcmoddev.lib.material.MMDMaterial;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemBlock;

public class BlockApi implements IBlockApi {
	public static final BlockApi instance = new BlockApi();
	
	@Override
	public String getApiName() {
		return "MMDLIb::BlockAPI";
	}

	@Override
	public Pair<? extends Block, ? extends ItemBlock> createBlock(String typeName, MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IBlockState getBlock(String typeName, MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IBlockApi addBlock(String typeName, MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IBlockApi addBlock(String typeName, String serializedState, MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IBlockApi addBlock(String typeName, Block block, ItemBlock itemBlock, MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IBlockApi addBlock(String typeName, IBlockState blockState, ItemBlock itemBlock, MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}


}
