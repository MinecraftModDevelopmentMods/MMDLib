package com.mcmoddev.lib.block;

import java.util.Random;

import com.mcmoddev.lib.data.Names;
import com.mcmoddev.lib.material.IMMDObject;
import com.mcmoddev.lib.material.MMDMaterial;

import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Ore Block.
 */
public class BlockMMDOre extends net.minecraft.block.BlockOre implements IMMDObject {

	private final MMDMaterial mmdMaterial;

	/**
	 *
	 * @param material
	 *            The material the ore is made from
	 */
	public BlockMMDOre(final MMDMaterial material) {
		this(material, false);
	}

	/**
	 *
	 * @param material The material the ore is made from
	 * @param isSoft is this a soft block?
	 */
	public BlockMMDOre(final MMDMaterial material, final boolean isSoft) {
		super();
		this.mmdMaterial = material;
		float hardnessMax = 5f;
		float resistMax = 1.5f;
		String tool = "pickaxe";

		if (isSoft) {
			this.setSoundType(SoundType.GROUND);
			hardnessMax = 2.5f;
			resistMax = 1.5f;
			tool = "shovel";
		} else {
			this.setSoundType(SoundType.STONE);
		}

		this.blockHardness = Math.max(hardnessMax, this.mmdMaterial.getOreBlockHardness());
		this.blockResistance = Math.max(resistMax, this.mmdMaterial.getBlastResistance() * 0.75f);
		this.setHarvestLevel(tool, this.mmdMaterial.getRequiredHarvestLevel());
	}

	@Override
	public int getExpDrop(final IBlockState bs, final IBlockAccess w, final BlockPos coord,
			final int i) {
		return 0; // XP comes from smelting
	}

	@Override
	public boolean canEntityDestroy(final IBlockState bs, final IBlockAccess w,
			final BlockPos coord, final Entity entity) {
		//TODO: FFS, this is in 3 different classes - should we add a "can destroy" to the material instead ?
		/*
		if ((this == Materials.getMaterialByName(MaterialNames.STARSTEEL).getBlock(Names.ORE))
				&& (entity instanceof net.minecraft.entity.boss.EntityDragon)) {
			return false;
		}*/
		return super.canEntityDestroy(bs, w, coord, entity);
	}

	@Override
	public MMDMaterial getMMDMaterial() {
		return this.mmdMaterial;
	}

	@Override
	public int quantityDropped(final IBlockState state, final int fortune, final Random random) {
		int most = 1;
		int least = 1;
		int total;
		switch (this.mmdMaterial.getType()) {
			case WOOD:
			case ROCK:
			case METAL:
				// stupid hack - but at least this doesn't get into a race where
				// users have figured out how to subvert putting a "this was placed by the user"
				// chunk of data on the ores situation to get around the fortune drops
				return 1;
			case MINERAL:
				most = 4;
				least = 2;
				break;
			case CRYSTAL:
				most = 4;
				least = 1;
				break;
			case GEM:
				most = 3;
				least = 2;
				break;
			default:
				return 1;
		}
		total = ((most - least) + fortune) + 1;
		return least + random.nextInt(total);
	}

	@Override
	public Item getItemDropped(final IBlockState state, final Random random, final int fortune) {
		switch (this.mmdMaterial.getType()) {
			case CRYSTAL:
				return this.mmdMaterial.getItem(Names.CRYSTAL);
			case GEM:
				return this.mmdMaterial.getItem(Names.GEM);
			case MINERAL:
				return this.mmdMaterial.getItem(Names.POWDER);
			default:
				return Item.getItemFromBlock(this);
		}
	}
}
