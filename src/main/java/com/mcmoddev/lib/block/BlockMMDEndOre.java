package com.mcmoddev.lib.block;

import com.mcmoddev.lib.data.MaterialNames;
import com.mcmoddev.lib.data.Names;
import com.mcmoddev.lib.init.Materials;
import com.mcmoddev.lib.material.IMMDObject;
import com.mcmoddev.lib.material.MMDMaterial;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * End Ore Block.
 */
public class BlockMMDEndOre extends net.minecraft.block.BlockOre implements IMMDObject {

	private final MMDMaterial material;

	/**
	 *
	 * @param material
	 *            The material the ore is made from
	 */
	public BlockMMDEndOre(final MMDMaterial material) {
		super();
		this.material = material;
		this.setSoundType(SoundType.STONE);
		this.blockHardness = Math.max(5f, this.material.getOreBlockHardness());
		this.blockResistance = Math.max(1.5f, this.material.getBlastResistance() * 0.75f);
		this.setHarvestLevel(this.material.getHarvestTool(), this.material.getRequiredHarvestLevel());
	}

	@Override
	public int getExpDrop(final IBlockState bs, final IBlockAccess w, final BlockPos coord, final int i) {
		return 0; // XP comes from smelting
	}

	@Override
	public boolean canEntityDestroy(final IBlockState bs, final IBlockAccess w, final BlockPos coord, final Entity entity) {
		if ((this == Materials.getMaterialByName(MaterialNames.STARSTEEL).getBlock(Names.ORE)) && (entity instanceof net.minecraft.entity.boss.EntityDragon)) {
			return false;
		}
		return super.canEntityDestroy(bs, w, coord, entity);
	}

	@Override
	public MMDMaterial getMMDMaterial() {
		return this.material;
	}
}
