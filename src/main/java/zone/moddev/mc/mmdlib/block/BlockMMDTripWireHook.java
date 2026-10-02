package zone.moddev.mc.mmdlib.block;

import zone.moddev.mc.mmdlib.material.IMMDObject;
import zone.moddev.mc.mmdlib.material.MMDMaterial;

public class BlockMMDTripWireHook extends net.minecraft.block.BlockTripWireHook
		implements IMMDObject {

	private final MMDMaterial mmdMaterial;

	/**
	 *
	 * @param material The material the Tripwire Hook is made from
	 */
	public BlockMMDTripWireHook(final MMDMaterial material) {
		super();
		this.mmdMaterial = material;
		this.setSoundType(this.mmdMaterial.getSoundType());
		this.blockHardness = this.mmdMaterial.getBlockHardness();
		this.blockResistance = this.mmdMaterial.getBlastResistance();
		this.setHarvestLevel(this.mmdMaterial.getHarvestTool(),
				this.mmdMaterial.getRequiredHarvestLevel());
	}

	@Override
	public MMDMaterial getMMDMaterial() {
		return this.mmdMaterial;
	}
}
