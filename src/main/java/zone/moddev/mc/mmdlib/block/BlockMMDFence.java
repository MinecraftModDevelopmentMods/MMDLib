package zone.moddev.mc.mmdlib.block;

import zone.moddev.mc.mmdlib.material.IMMDObject;
import zone.moddev.mc.mmdlib.material.MMDMaterial;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public class BlockMMDFence extends net.minecraft.block.BlockFence implements IMMDObject {

	private final MMDMaterial mmdMaterial;

	/**
	 *
	 * @param material The material the Fence is made from
	 */
	public BlockMMDFence(final MMDMaterial material) {
		super(material.getVanillaMaterial(), material.getVanillaMaterial().getMaterialMapColor());
		this.mmdMaterial = material;
		this.setSoundType(this.mmdMaterial.getSoundType());
		this.blockHardness = this.mmdMaterial.getBlockHardness();
		this.blockResistance = this.mmdMaterial.getBlastResistance();
		this.setHarvestLevel(this.mmdMaterial.getHarvestTool(),
				this.mmdMaterial.getRequiredHarvestLevel());
	}

	// All states of this block support a torch on top.
	@Override
	public boolean canPlaceTorchOnTop(final IBlockState state, final IBlockAccess world,
			final BlockPos pos) {
		return true;
	}

	@Override
	public MMDMaterial getMMDMaterial() {
		return this.mmdMaterial;
	}
}
