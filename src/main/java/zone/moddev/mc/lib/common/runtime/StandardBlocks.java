package zone.moddev.mc.lib.common.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import zone.moddev.mc.lib.common.types.MaterialPart;
import java.util.function.Supplier;

/** Select actual vanilla or BMe-derived stateful classes; never fall back for an unknown behavior. */
public final class StandardBlocks {
    private StandardBlocks() {}
    public static class Bookshelf extends Block {
        public Bookshelf(Properties properties) { super(properties); }
        @Override public float getEnchantPowerBonus(BlockState state, LevelReader level, BlockPos pos) { return 1; }
    }
    public static Class<? extends Block> blockClass(MaterialPart part) {
        return switch (part) {
            case BLOCK -> Block.class; case ORE, ENDORE, NETHERORE -> DropExperienceBlock.class;
            case PLATE -> PlateBlock.class; case BARS -> IronBarsBlock.class; case BOOKSHELF -> Bookshelf.class;
            case BUTTON -> ButtonBlock.class; case SLAB -> SlabBlock.class; case DOUBLE_SLAB -> CompatibilityDoubleSlabBlock.class;
            case FLOWER_POT -> FlowerPotBlock.class; case LADDER -> LadderBlock.class; case LEVER -> LeverBlock.class;
            case PRESSURE_PLATE -> PressurePlateBlock.class; case STAIRS -> StairBlock.class; case TRAPDOOR -> TrapDoorBlock.class;
            case TRIPWIRE_HOOK -> TripWireHookBlock.class; case WALL -> WallBlock.class; case FENCE -> FenceBlock.class;
            case FENCE_GATE -> FenceGateBlock.class; case ANVIL -> MaterialAnvilBlock.class; case DOOR -> DoorBlock.class;
            default -> throw new IllegalArgumentException("Not a supported standard block: " + part);
        };
    }
    public static Block create(MaterialPart part, BlockBehaviour.Properties properties, BlockSetType setType,
                               WoodType woodType, Supplier<BlockState> stairBase) {
        return switch (part) {
            case BLOCK -> new Block(properties);
            case ORE, ENDORE, NETHERORE -> new DropExperienceBlock(ConstantInt.of(0), properties);
            case PLATE -> new PlateBlock(properties.noOcclusion());
            case BARS -> new IronBarsBlock(properties.noOcclusion().dynamicShape());
            case BOOKSHELF -> new Bookshelf(properties);
            case BUTTON -> new ButtonBlock(setType, 20, properties.noCollission());
            case SLAB -> new SlabBlock(properties);
            case DOUBLE_SLAB -> new CompatibilityDoubleSlabBlock(properties);
            case FLOWER_POT -> new FlowerPotBlock(Blocks.AIR, properties.noOcclusion());
            case LADDER -> new LadderBlock(properties.noOcclusion());
            case LEVER -> new LeverBlock(properties.noCollission());
            case PRESSURE_PLATE -> new PressurePlateBlock(setType, properties);
            case STAIRS -> new StairBlock(stairBase.get(), properties);
            case TRAPDOOR -> new TrapDoorBlock(setType, properties.noOcclusion().dynamicShape());
            case TRIPWIRE_HOOK -> new TripWireHookBlock(properties.noCollission());
            case WALL -> new WallBlock(properties); case FENCE -> new FenceBlock(properties);
            case FENCE_GATE -> new FenceGateBlock(woodType, properties);
            case ANVIL -> new MaterialAnvilBlock(properties);
            case DOOR -> new DoorBlock(setType, properties.noOcclusion().dynamicShape());
            default -> throw new IllegalArgumentException("Not a supported standard block: " + part);
        };
    }
}
