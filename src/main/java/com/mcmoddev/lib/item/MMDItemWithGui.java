package com.mcmoddev.lib.item;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import com.mcmoddev.lib.util.ItemGuiUtils;
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
    @ParametersAreNonnullByDefault
    public EnumActionResult onItemUse(EntityPlayer player, World worldIn, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        EnumActionResult result = super.onItemUse(player, worldIn, pos, hand, facing, hitX, hitY, hitZ);
        return ItemGuiUtils.onItemUse(this, result, player, worldIn, pos, hand);
    }

    @Override
    @Nonnull
    @ParametersAreNonnullByDefault
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        ActionResult<ItemStack> result = ItemGuiUtils.onItemRightClick(this, worldIn, playerIn, handIn);
        return (result != null) ? result : super.onItemRightClick(worldIn, playerIn, handIn);
    }
}
