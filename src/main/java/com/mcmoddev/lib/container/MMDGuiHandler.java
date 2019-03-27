package com.mcmoddev.lib.container;

import javax.annotation.Nullable;
import com.mcmoddev.lib.MMDLib;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;

/**
 * MMDLib implementation of {@link IGuiHandler}.
 */
public class MMDGuiHandler implements IGuiHandler {
    /**
     * The unique instance of MMDGuiHandler.
     */
    public final static MMDGuiHandler INSTANCE = new MMDGuiHandler();
    protected MMDGuiHandler() {}

    /**
     * Registers this handler to the {@link NetworkRegistry}.
     */
    public static void init() {
        NetworkRegistry.INSTANCE.registerGuiHandler(MMDLib.instance, MMDGuiHandler.INSTANCE);
    }

    @Nullable
    private IGuiProvider getGuiProvider(final int ID, final EntityPlayer player, final World world, final int x, final int y, final int z) {
        final int id = ID & 7; // 111
        if (id == GuiHandlerIds.GUI_TILE.getValue()) {
            final TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
            if (tile instanceof IGuiProvider) {
                return IGuiProvider.class.cast(tile);
            }
        }
        else if ((id == GuiHandlerIds.GUI_ITEM_MAIN_HAND.getValue()) || (id == GuiHandlerIds.GUI_ITEM_OFF_HAND.getValue())) {
            final ItemStack stack;
            if (id == GuiHandlerIds.GUI_ITEM_MAIN_HAND.getValue()) {
                stack = player.getHeldItemMainhand();
            }
            else {
                stack = player.getHeldItemOffhand();
            }

            if (!stack.isEmpty()) {
                final Item item = stack.getItem();
                IGuiProvider provider = null;
                if (item instanceof IItemStackGuiProvider) {
                    provider = IItemStackGuiProvider.class.cast(item).getGuiProvider(stack);
                }
                else if (item instanceof IGuiProvider) {
                    provider = IGuiProvider.class.cast(item);
                }

                return provider;
            }
        }

        return null;
    }

    @Nullable
    @Override
    public Object getServerGuiElement(final int ID, final EntityPlayer player, final World world, final int x, final int y, final int z) {
        final IGuiProvider provider = this.getGuiProvider(ID, player, world, x, y, z);
        return (provider == null) ? null : provider.getServerGui(ID >> 3, player, world, x, y, z);
    }

    @Nullable
    @Override
    public Object getClientGuiElement(final int ID, final EntityPlayer player, final World world, final int x, final int y, final int z) {
        final IGuiProvider provider = this.getGuiProvider(ID, player, world, x, y, z);
        return (provider == null) ? null : provider.getClientGui(ID >> 3, player, world, x, y, z);
    }
}
