package com.mcmoddev.lib.item;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.gui.GuiHandlerIds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@SuppressWarnings("WeakerAccess")
public class MMDItemWithGui extends Item {
    @Override
    @Nonnull
    public EnumActionResult onItemUse(EntityPlayer player, World worldIn, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        // TODO: move this code to a helper class
        EnumActionResult result = super.onItemUse(player, worldIn, pos, hand, facing, hitX, hitY, hitZ);
        if ((worldIn != null) && (player != null) && !worldIn.isRemote && (result == EnumActionResult.PASS) && (hand == player.getActiveHand())) {
            player.openGui(this.getModInstance(worldIn),
                (2 << 3) + ((hand == EnumHand.MAIN_HAND) ? GuiHandlerIds.GUI_ITEM_MAIN_HAND.getValue() : GuiHandlerIds.GUI_ITEM_OFF_HAND.getValue()),
                worldIn, pos.getX(), pos.getY(), pos.getZ());
            return EnumActionResult.SUCCESS;
        }
        return result;
    }

    @Override
    @Nonnull
    @ParametersAreNonnullByDefault
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        // TODO: move this code to a helper class
        if ((worldIn != null) && !worldIn.isRemote && (playerIn != null) && (handIn == playerIn.getActiveHand())) {
            playerIn.openGui(this.getModInstance(worldIn),
                (1 << 3) + ((handIn == EnumHand.MAIN_HAND) ? GuiHandlerIds.GUI_ITEM_MAIN_HAND.getValue() : GuiHandlerIds.GUI_ITEM_OFF_HAND.getValue()),
                worldIn, playerIn.getPosition().getX(), playerIn.getPosition().getY(), playerIn.getPosition().getZ());
            return new ActionResult<>(EnumActionResult.SUCCESS, playerIn.getHeldItem(handIn));
        }
        return super.onItemRightClick(worldIn, playerIn, handIn);
    }

    protected Object getModInstance(World world) {
        return MMDLib.instance;
    }
}
