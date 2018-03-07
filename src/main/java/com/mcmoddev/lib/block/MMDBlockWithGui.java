package com.mcmoddev.lib.block;

import javax.annotation.ParametersAreNonnullByDefault;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.container.GuiHandlerIds;
import com.mcmoddev.lib.container.IGuiProvider;
import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import mcp.MethodsReturnNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@SuppressWarnings("WeakerAccess")
public class MMDBlockWithGui extends Block {
    public MMDBlockWithGui(final Material blockMaterialIn, final MapColor blockMapColorIn) {
        super(blockMaterialIn, blockMapColorIn);
    }

    public MMDBlockWithGui(final Material materialIn) {
        super(materialIn);
    }

    @Override
    public boolean onBlockActivated(final World worldIn, final BlockPos pos, final IBlockState state, final EntityPlayer playerIn, final EnumHand hand, final EnumFacing facing, final float hitX, final float hitY, final float hitZ) {
        if (super.onBlockActivated(worldIn, pos, state, playerIn, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }

        if ((worldIn != null) && (pos != null)/* && !worldIn.isRemote && (playerIn instanceof EntityPlayerMP)*/) {
//            final EntityPlayerMP player = (EntityPlayerMP)playerIn;
            final int guiId = this.getGuiId(worldIn, pos, playerIn, hand, facing, hitX, hitY, hitZ);
            if (guiId >= 0) {
                if (worldIn.isRemote) {
                    return true;
                }
                return this.openGui(guiId, worldIn, pos, playerIn);
            }
        }

        return false;
    }

    protected int getGuiId(final World world, final BlockPos pos, final EntityPlayer player, final EnumHand hand, final EnumFacing facing, final float hitX, final float hitY, final float hitZ) {
        final TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof IGuiProvider) {
            return GuiHandlerIds.GUI_TILE.getValue();
        }
        return -1;
    }

    protected boolean openGui(final int guiId, final World world, final BlockPos pos, final EntityPlayer player) {
        player.openGui(this.getModInstance(world, pos), guiId, world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    protected Object getModInstance(final World world, final BlockPos pos) {
        return MMDLib.instance;
    }
}
