package com.mcmoddev.lib.block;

import com.mcmoddev.lib.material.IMMDObject;
import com.mcmoddev.lib.material.MMDMaterial;
import com.mcmoddev.lib.material.MMDMaterial.MaterialType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockMMDButton extends net.minecraft.block.BlockButton implements IMMDObject {

	private final MMDMaterial mmdMaterial;

	/**
	 *
	 * @param mmdMaterialIn
	 */
	public BlockMMDButton(final MMDMaterial mmdMaterialIn) {
		super((mmdMaterialIn.getType() == MaterialType.WOOD) ? true : false);
		this.mmdMaterial = mmdMaterialIn;
		this.setSoundType(this.mmdMaterial.getSoundType());
		this.blockHardness = this.mmdMaterial.getBlockHardness();
		this.blockResistance = this.mmdMaterial.getBlastResistance();
		this.setHarvestLevel(this.mmdMaterial.getHarvestTool(), this.mmdMaterial.getRequiredHarvestLevel());
	}

	@Override
	protected void playClickSound(final EntityPlayer player, final World worldIn, final BlockPos pos) {
		SoundEvent soundEvent = SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON;
        if (this.material == Material.WOOD) {
    		soundEvent = SoundEvents.BLOCK_WOOD_BUTTON_CLICK_ON;
		}
		worldIn.playSound(player, pos, soundEvent, SoundCategory.BLOCKS, 0.3F, 0.6F);
	}

	@Override
	protected void playReleaseSound(final World worldIn, final BlockPos pos) {
		SoundEvent soundEvent = SoundEvents.BLOCK_STONE_BUTTON_CLICK_OFF;
        if (this.material == Material.WOOD) {
    		soundEvent = SoundEvents.BLOCK_WOOD_BUTTON_CLICK_OFF;
		}
		worldIn.playSound((EntityPlayer) null, pos, soundEvent, SoundCategory.BLOCKS, 0.3F, 0.5F);
	}

	@Override
	public MMDMaterial getMMDMaterial() {
		return this.mmdMaterial;
	}
}
