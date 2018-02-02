package com.mcmoddev.lib.block;

import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.container.GuiHandlerIds;
import com.mcmoddev.lib.container.IGuiProvider;
import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@SuppressWarnings("WeakerAccess")
public class MMDBlockWithGui extends Block {
    public MMDBlockWithGui(Material blockMaterialIn, MapColor blockMapColorIn) {
        super(blockMaterialIn, blockMapColorIn);
    }

    public MMDBlockWithGui(Material materialIn) {
        super(materialIn);
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(worldIn, pos, state, playerIn, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }

        if ((worldIn != null) && (pos != null) && !worldIn.isRemote && (playerIn instanceof EntityPlayerMP)) {
            EntityPlayerMP player = (EntityPlayerMP)playerIn;
            int guiId = this.getGuiId(worldIn, pos, player, hand, facing, hitX, hitY, hitZ);
            if (guiId >= 0) {
                return this.openGui(guiId, worldIn, pos, player);
            }
        }

        return false;
    }

    protected int getGuiId(World world, BlockPos pos, EntityPlayerMP player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof IGuiProvider) {
            return GuiHandlerIds.GUI_TILE.getValue();
        }
        return -1;
    }

    protected boolean openGui(int guiId, World world, BlockPos pos, EntityPlayerMP player) {
        player.openGui(this.getModInstance(world, pos), guiId, world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    protected Object getModInstance(World world, BlockPos pos) {
        return MMDLib.instance;
    }
}
