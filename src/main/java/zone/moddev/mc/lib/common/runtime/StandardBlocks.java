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
        return switch (part.factory().toString()) {
            case "mmdlib:block" -> Block.class; case "mmdlib:ore" -> DropExperienceBlock.class;
            case "mmdlib:plate" -> PlateBlock.class; case "mmdlib:bars" -> IronBarsBlock.class; case "mmdlib:bookshelf" -> Bookshelf.class;
            case "mmdlib:button" -> ButtonBlock.class; case "mmdlib:slab" -> SlabBlock.class; case "mmdlib:double_slab" -> CompatibilityDoubleSlabBlock.class;
            case "mmdlib:flower_pot" -> FlowerPotBlock.class; case "mmdlib:ladder" -> LadderBlock.class; case "mmdlib:lever" -> LeverBlock.class;
            case "mmdlib:pressure_plate" -> PressurePlateBlock.class; case "mmdlib:stairs" -> StairBlock.class; case "mmdlib:trapdoor" -> TrapDoorBlock.class;
            case "mmdlib:tripwire_hook" -> TripWireHookBlock.class; case "mmdlib:wall" -> WallBlock.class; case "mmdlib:fence" -> FenceBlock.class;
            case "mmdlib:fence_gate" -> FenceGateBlock.class; case "mmdlib:anvil" -> MaterialAnvilBlock.class; case "mmdlib:door" -> DoorBlock.class;
            default -> throw new IllegalArgumentException("Not a supported standard block: " + part);
        };
    }
    public static Block create(MaterialPart part, BlockBehaviour.Properties properties, BlockSetType setType,
                               WoodType woodType, Supplier<BlockState> stairBase) {
        return switch (part.factory().toString()) {
            case "mmdlib:block" -> new Block(properties);
            case "mmdlib:ore" -> new DropExperienceBlock(ConstantInt.of(0), properties);
            case "mmdlib:plate" -> new PlateBlock(properties.noOcclusion());
            case "mmdlib:bars" -> new IronBarsBlock(properties.noOcclusion().dynamicShape());
            case "mmdlib:bookshelf" -> new Bookshelf(properties);
            case "mmdlib:button" -> new ButtonBlock(setType, 20, properties.noCollission());
            case "mmdlib:slab" -> new SlabBlock(properties);
            case "mmdlib:double_slab" -> new CompatibilityDoubleSlabBlock(properties);
            case "mmdlib:flower_pot" -> new FlowerPotBlock(Blocks.AIR, properties.noOcclusion());
            case "mmdlib:ladder" -> new LadderBlock(properties.noOcclusion());
            case "mmdlib:lever" -> new LeverBlock(properties.noCollission());
            case "mmdlib:pressure_plate" -> new PressurePlateBlock(setType, properties);
            case "mmdlib:stairs" -> new StairBlock(stairBase.get(), properties);
            case "mmdlib:trapdoor" -> new TrapDoorBlock(setType, properties.noOcclusion().dynamicShape());
            case "mmdlib:tripwire_hook" -> new TripWireHookBlock(properties.noCollission());
            case "mmdlib:wall" -> new WallBlock(properties); case "mmdlib:fence" -> new FenceBlock(properties);
            case "mmdlib:fence_gate" -> new FenceGateBlock(woodType, properties);
            case "mmdlib:anvil" -> new MaterialAnvilBlock(properties);
            case "mmdlib:door" -> new DoorBlock(setType, properties.noOcclusion().dynamicShape());
            default -> throw new IllegalArgumentException("Not a supported standard block: " + part);
        };
    }
}
