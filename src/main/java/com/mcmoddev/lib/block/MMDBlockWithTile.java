package com.mcmoddev.lib.block;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.tile.MMDTileEntity;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;
import mcp.MethodsReturnNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MMDBlockWithTile<T extends MMDTileEntity> extends MMDBlockWithGui implements ITileEntityProvider {
    private final Class<T> tileClass;

    public MMDBlockWithTile(final Class<T> tileClass, final Material materialIn) {
        super(materialIn);
        this.tileClass = tileClass;
    }

    public MMDBlockWithTile(final Class<T> tileClass, final Material materialIn, final MapColor blockMapColorIn) {
        super(materialIn, blockMapColorIn);
        this.tileClass = tileClass;
    }

    public void registerTile() {
        final ResourceLocation key = this.getRegistryName();
        if (key != null) {
            GameRegistry.registerTileEntity(this.tileClass, key.toString() + "_tile");
        }
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(final World worldIn, final int meta) {
        Constructor<T> metaConstructor;
        try {
            // looking for constructor based on meta
            // does anyone really use meta for tile entities?!?
            metaConstructor = this.tileClass.getConstructor(int.class);
        } catch (final NoSuchMethodException e) {
            metaConstructor = null;
        }
        if (metaConstructor != null) {
            try {
                return metaConstructor.newInstance(meta);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                MMDLib.logger.error("Error creating tile entity!", ex);
            }
        }

        Constructor<T> constructor;
        try {
            constructor = this.tileClass.getConstructor();
        } catch (final NoSuchMethodException e) {
            constructor = null;
        }
        if (constructor != null) {
            try {
                return constructor.newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                MMDLib.logger.error("Error creating tile entity!", ex);
            }
        }

        // no suitable constructor found
        return null;
    }

    // TODO: maybe the tile entity NBT to creative pick block
}
