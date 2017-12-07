package com.mcmoddev.lib.item;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.container.MMDContainer;
import com.mcmoddev.lib.gui.GuiHandlerIds;
import com.mcmoddev.lib.gui.IGuiHolder;
import com.mcmoddev.lib.gui.IGuiProvider;
import com.mcmoddev.lib.gui.MMDGuiContainer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings("WeakerAccess")
public class MMDItemWithGui extends Item implements IGuiProvider, IGuiHolder {
    @Override
    @Nonnull
    public EnumActionResult onItemUse(EntityPlayer player, World worldIn, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
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

    public enum GuiType {
        AIR, BLOCK
    }

    @Override
    @SideOnly(Side.CLIENT)
    public final GuiContainer getClientGui(int id, @Nonnull EntityPlayer player, @Nonnull World world, int x, int y, int z) {
        return this.getClientGui((id == 2) ? GuiType.BLOCK : GuiType.AIR, player, player.getActiveItemStack(), world, x, y, z);
    }

    @SideOnly(Side.CLIENT)
    protected GuiContainer getClientGui(GuiType type, EntityPlayer player, ItemStack stack, @Nonnull World world, int x, int y, int z) {
        return new MMDGuiContainer(this, player, this.getServerGui(type, player, stack, world, x, y, z));
    }

    @Override
    public final Container getServerGui(int id, @Nonnull EntityPlayer player, @Nonnull World world, int x, int y, int z) {
        return this.getServerGui((id == 2) ? GuiType.BLOCK : GuiType.AIR, player, player.getActiveItemStack(), world, x, y, z);
    }

    protected MMDContainer getServerGui(GuiType type, EntityPlayer player, ItemStack stack, @Nonnull World world, int x, int y, int z) {
        return new MMDContainer(this, player);
    }
}
