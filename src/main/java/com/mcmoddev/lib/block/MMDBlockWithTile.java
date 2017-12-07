package com.mcmoddev.lib.block;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import javax.annotation.Nullable;
import com.mcmoddev.lib.tile.MMDTileEntity;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class MMDBlockWithTile<T extends MMDTileEntity> extends MMDBlockWithGui implements ITileEntityProvider {
    private final Class<T> tileClass;

    public MMDBlockWithTile(Class<T> tileClass, Material materialIn) {
        super(materialIn);
        this.tileClass = tileClass;
    }

    public MMDBlockWithTile(Class<T> tileClass, Material materialIn, MapColor blockMapColorIn) {
        super(materialIn, blockMapColorIn);
        this.tileClass = tileClass;
    }

    public void registerTile() {
        ResourceLocation key = this.getRegistryName();
        if (key != null) {
            GameRegistry.registerTileEntity(this.tileClass, key.toString() + "_tile");
        }
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        Constructor<T> metaConstructor = null;
        try {
            // looking for constructor based on meta
            // does anyone really use meta for tile entities?!?
            metaConstructor = this.tileClass.getConstructor(int.class);
        } catch (NoSuchMethodException e) {
            metaConstructor = null;
        }
        if (metaConstructor != null) {
            try {
                return metaConstructor.newInstance(meta);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ignored) { }
        }

        Constructor<T> constructor = null;
        try {
            constructor = this.tileClass.getConstructor();
        } catch (NoSuchMethodException e) {
            constructor = null;
        }
        if (constructor != null) {
            try {
                return constructor.newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ignored) { }
        }

        // no suitable constructor found
        return null;
    }

    // TODO: maybe the tile entity NBT to creative pick block
}
