package com.mcmoddev.lib.gui;

import javax.annotation.Nullable;
import com.mcmoddev.lib.MMDLib;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@SuppressWarnings("WeakerAccess")
public class MMDGuiHandler implements IGuiHandler {
    public final static MMDGuiHandler INSTANCE = new MMDGuiHandler();
    protected MMDGuiHandler() {}

    public static void init() {
        NetworkRegistry.INSTANCE.registerGuiHandler(MMDLib.instance, MMDGuiHandler.INSTANCE);
    }

    @Nullable
    private IGuiProvider getGuiProvider(int ID, EntityPlayer player, World world, int x, int y, int z) {
        int id = ID & 7; // 111
        if (id == GuiHandlerIds.GUI_TILE.getValue()) {
            TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
            if (tile instanceof IGuiProvider) {
                return ((IGuiProvider) tile);
            }
        }
        else if ((id == GuiHandlerIds.GUI_ITEM_MAIN_HAND.getValue()) || (id == GuiHandlerIds.GUI_ITEM_OFF_HAND.getValue())) {
            ItemStack stack;
            if (id == GuiHandlerIds.GUI_ITEM_MAIN_HAND.getValue()) {
                stack = player.getHeldItemMainhand();
            }
            else {
                stack = player.getHeldItemOffhand();
            }

            if (!stack.isEmpty() && (stack.getItem() instanceof IGuiProvider)) {
                return ((IGuiProvider)stack.getItem());
            }
        }

        return null;
    }

    @Nullable
    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        IGuiProvider provider = this.getGuiProvider(ID, player, world, x, y, z);
        return (provider == null) ? null : provider.getServerGui(ID >> 3, player, world, x, y, z);
    }

    @Nullable
    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        IGuiProvider provider = this.getGuiProvider(ID, player, world, x, y, z);
        return (provider == null) ? null : provider.getClientGui(ID >> 3, player, world, x, y, z);
    }
}
