package com.mcmoddev.lib.API;

import org.apache.commons.lang3.tuple.Pair;

import com.mcmoddev.lib.material.MMDMaterial;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemBlock;

public interface IBlockApi extends IMMDApi {
	default String getApiName() {
		return "MMDLib Block->Material Registration API";
	}
	
	Pair<? extends Block, ? extends ItemBlock> createBlock( String typeName, MMDMaterial material);
	IBlockState getBlock( String typeName, MMDMaterial material );

	IBlockApi addBlock( String typeName, MMDMaterial material );
	IBlockApi addBlock( String typeName, String serializedState, MMDMaterial material );
	IBlockApi addBlock( String typeName, Block block, ItemBlock itemBlock, MMDMaterial material );
	IBlockApi addBlock( String typeName, IBlockState blockState, ItemBlock itemBlock, MMDMaterial material );
}
