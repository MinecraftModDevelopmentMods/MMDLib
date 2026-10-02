package zone.moddev.mc.mmdlib.block;

import zone.moddev.mc.mmdlib.material.IMMDObject;
import zone.moddev.mc.mmdlib.material.MMDMaterial;

import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Nether Ore Block.
 */
public class BlockMMDNetherOre extends BlockExplosiveOre implements IMMDObject {

	private final MMDMaterial mmdMaterial;

	/**
	 *
	 * @param material
	 *            The material the ore is made from
	 */
	public BlockMMDNetherOre(final MMDMaterial material) {
		super();
		this.mmdMaterial = material;
		this.setSoundType(SoundType.STONE);
		this.blockHardness = Math.max(5f, this.mmdMaterial.getOreBlockHardness());
		this.blockResistance = Math.max(1.5f, this.mmdMaterial.getBlastResistance() * 0.75f);
		this.setHarvestLevel(this.mmdMaterial.getHarvestTool(),
				this.mmdMaterial.getRequiredHarvestLevel());
	}

	@Override
	public int getExpDrop(final IBlockState bs, final IBlockAccess w, final BlockPos coord,
			final int i) {
		return 0; // XP comes from smelting
	}

	@Override
	public boolean canEntityDestroy(final IBlockState bs, final IBlockAccess w,
			final BlockPos coord, final Entity entity) {

		return super.canEntityDestroy(bs, w, coord, entity);
	}

	@Override
	public MMDMaterial getMMDMaterial() {
		return this.mmdMaterial;
	}
}
