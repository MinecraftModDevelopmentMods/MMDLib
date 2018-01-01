package com.mcmoddev.lib.util;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.common.IModInstanceProvider;
import com.mcmoddev.lib.gui.GuiHandlerIds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import mcp.MethodsReturnNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ItemGuiUtils {
    private ItemGuiUtils() {}

    public static EnumActionResult onItemUse(Item item, EnumActionResult superResult, EntityPlayer player, World worldIn, BlockPos pos, EnumHand hand) {
        // TODO: move this code to a helper class
        if (!worldIn.isRemote && (superResult == EnumActionResult.PASS) && (hand == player.getActiveHand())) {
            player.openGui(ItemGuiUtils.getModInstance(item, worldIn),
                (2 << 3) + ((hand == EnumHand.MAIN_HAND) ? GuiHandlerIds.GUI_ITEM_MAIN_HAND.getValue() : GuiHandlerIds.GUI_ITEM_OFF_HAND.getValue()),
                worldIn, pos.getX(), pos.getY(), pos.getZ());
            return EnumActionResult.SUCCESS;
        }
        return superResult;
    }

    @Nullable
    public static ActionResult<ItemStack> onItemRightClick(Item item, World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        // TODO: move this code to a helper class
        if (!worldIn.isRemote && (handIn == playerIn.getActiveHand())) {
            playerIn.openGui(ItemGuiUtils.getModInstance(item, worldIn),
                (1 << 3) + ((handIn == EnumHand.MAIN_HAND) ? GuiHandlerIds.GUI_ITEM_MAIN_HAND.getValue() : GuiHandlerIds.GUI_ITEM_OFF_HAND.getValue()),
                worldIn, playerIn.getPosition().getX(), playerIn.getPosition().getY(), playerIn.getPosition().getZ());
            return new ActionResult<>(EnumActionResult.SUCCESS, playerIn.getHeldItem(handIn));
        }
        return null;
    }

    private static Object getModInstance(Object thing, World world) {
        if (thing instanceof IModInstanceProvider) {
            return IModInstanceProvider.class.cast(thing).getModInstance(world);
        }
        return MMDLib.instance;
    }
}
