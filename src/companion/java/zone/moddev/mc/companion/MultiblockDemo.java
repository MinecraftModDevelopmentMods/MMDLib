package zone.moddev.mc.companion;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import zone.moddev.mc.lib.machine.multiblock.FixedLayout;
import zone.moddev.mc.lib.machine.multiblock.MultiblockDefinition;
import zone.moddev.mc.lib.machine.multiblock.StructureMember;
import zone.moddev.mc.lib.machine.multiblock.StructurePort;
import zone.moddev.mc.lib.machine.multiblock.StructureRequirement;
import zone.moddev.mc.lib.machine.neoforge.MultiblockBlock;
import zone.moddev.mc.lib.machine.neoforge.MultiblockItem;
import zone.moddev.mc.lib.machine.neoforge.MultiblockPartEntity;
import zone.moddev.mc.lib.machine.neoforge.MultiblockPorts;

/** Playable three-position fixture for whole-machine placement and remote resource ports. */
public final class MultiblockDemo {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK,
        CompanionMod.ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM,
        CompanionMod.ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister
        .create(Registries.BLOCK_ENTITY_TYPE, CompanionMod.ID);
    public static final DeferredHolder<Block, MultiblockBlock> CONTROLLER = BLOCKS.register(
        "multiblock_demo",
        () -> new MultiblockBlock(BlockBehaviour.Properties.of().strength(3),
            MultiblockDemo::controllerType));
    public static final DeferredHolder<Block, MultiblockBlock> PART = BLOCKS.register(
        "multiblock_part",
        () -> new MultiblockBlock(BlockBehaviour.Properties.of().strength(3),
            MultiblockDemo::partType, false));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockDemoEntity>> CONTROLLER_TYPE = TYPES
        .register("multiblock_demo",
            () -> BlockEntityType.Builder.of(MultiblockDemoEntity::new, CONTROLLER.get())
                .build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockPartEntity>> PART_TYPE = TYPES
        .register("multiblock_part",
            () -> BlockEntityType.Builder
                .of((pos, state) -> new MultiblockPartEntity(partType(), pos, state), PART.get())
                .build(null));
    public static final DeferredHolder<Item, MultiblockItem> ITEM = ITEMS.register(
        "multiblock_demo",
        () -> new MultiblockItem(new Item.Properties(), MultiblockDemo::definition));
    private static MultiblockDefinition definition;
    private MultiblockDemo() {
    }
    private static BlockEntityType<MultiblockDemoEntity> controllerType() {
        return CONTROLLER_TYPE.get();
    }
    private static BlockEntityType<MultiblockPartEntity> partType() {
        return PART_TYPE.get();
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        bus.addListener(
            (net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> MultiblockPorts
                .register(event, controllerType(), partType()));
    }
    public static MultiblockDefinition definition() {
        if (definition == null)
            definition = new MultiblockDefinition(CompanionMod.id("multiblock_demo"), 1,
                new FixedLayout(List.of(
                    new StructureMember("controller", "controller", BlockPos.ZERO,
                        CONTROLLER.get().defaultBlockState()),
                    new StructureMember("belt", "belt", new BlockPos(1, 0, 0),
                        PART.get().defaultBlockState()),
                    new StructureMember("socket", "socket", new BlockPos(2, 0, 0),
                        PART.get().defaultBlockState())),
                    List.of(new StructureRequirement("clearance", new BlockPos(1, 1, 0), true))),
                16, 8, MultiblockDefinition.Dismantling.REMOVE_OWNED,
                Map.of("clearance", (level, pos) -> level.getBlockState(pos).isAir()),
                List.of(
                    new StructurePort("socket", "energy", StructurePort.Kind.ENERGY,
                        Set.of(Direction.values()), false, true, true),
                    new StructurePort("socket", "items", StructurePort.Kind.ITEMS,
                        Set.of(Direction.values()), false, true, true),
                    new StructurePort("socket", "fluid", StructurePort.Kind.FLUIDS,
                        Set.of(Direction.values()), false, true, true)));
        return definition;
    }
}
