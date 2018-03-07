package com.mcmoddev.lib.block;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
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
    private final Supplier<T> tileClassCreator;

    public MMDBlockWithTile(final Class<T> tileClass, final Supplier<T> tileClassCreator, final Material materialIn) {
        super(materialIn);
        this.tileClass = tileClass;
        this.tileClassCreator = tileClassCreator;
    }

    public MMDBlockWithTile(final Class<T> tileClass, final Supplier<T> tileClassCreator, final Material materialIn, final MapColor blockMapColorIn) {
        super(materialIn, blockMapColorIn);
        this.tileClass = tileClass;
        this.tileClassCreator = tileClassCreator;
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
        return this.tileClassCreator.get();
    }

    // TODO: maybe the tile entity NBT to creative pick block
}
