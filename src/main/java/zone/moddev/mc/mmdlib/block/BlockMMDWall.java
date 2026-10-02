package zone.moddev.mc.mmdlib.block;

import zone.moddev.mc.mmdlib.data.Names;
import zone.moddev.mc.mmdlib.material.IMMDObject;
import zone.moddev.mc.mmdlib.material.MMDMaterial;

import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 *
 * @author Jasmine Iwanek
 *
 */
public class BlockMMDWall extends net.minecraft.block.BlockWall implements IMMDObject {

	private final MMDMaterial mmdMaterial;

	/**
	 *
	 * @param material
	 *            The material the wall is made from
	 */
	public BlockMMDWall(final MMDMaterial material) {
		super(material.getBlock(Names.BLOCK));
		this.mmdMaterial = material;
		this.setSoundType(this.mmdMaterial.getSoundType());
		this.blockHardness = this.mmdMaterial.getBlockHardness();
		this.blockResistance = this.mmdMaterial.getBlastResistance();
		this.setHarvestLevel(this.mmdMaterial.getHarvestTool(),
				this.mmdMaterial.getRequiredHarvestLevel());
	}

	@Override
	public void getSubBlocks(final CreativeTabs tab, final NonNullList<ItemStack> list) {
		list.add(new ItemStack(this));
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
