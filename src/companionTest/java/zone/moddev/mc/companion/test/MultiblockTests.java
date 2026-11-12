package zone.moddev.mc.companion.test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.companion.MultiblockDemo;
import zone.moddev.mc.companion.MultiblockDemoEntity;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.machine.multiblock.MultiblockInstance;
import zone.moddev.mc.lib.machine.multiblock.ResolvedStructure;
import zone.moddev.mc.lib.machine.multiblock.StructureMember;
import zone.moddev.mc.lib.machine.neoforge.MultiblockMembers;
import zone.moddev.mc.lib.machine.neoforge.MultiblockPlacement;
import zone.moddev.mc.lib.machine.neoforge.MultiblockPorts;

@GameTestHolder("mmdlib_companion")
@PrefixGameTestTemplate(false)
public final class MultiblockTests {
    @GameTest(template = "empty")
    public static void capabilityQueriesDoNotValidateOrDismantle(
        GameTestHelper helper) {
        var base = MultiblockDemo.definition();
        int[] validations = {0};
        var definition = new zone.moddev.mc.lib.machine.multiblock.MultiblockDefinition(
            base.id(), base.version(), base.resolver(), base.maxMembers(),
            base.maxExtent(), base.dismantling(),
            Map.of("clearance", (world, position) -> {
                validations[0]++;
                return world.getBlockState(position).isAir();
            }), base.ports());
        var level = helper.getLevel();
        var pos = origin(helper);
        var candidate = ((zone.moddev.mc.lib.machine.multiblock.FixedLayout) base
            .resolver()).structure();
        for (var member : candidate.members())
            level.setBlock(pos.offset(member.offset()), member.state(), 3);
        var entity = new MultiblockDemoEntity(pos, level.getBlockState(pos),
            definition);
        level.setBlockEntity(entity);
        require(entity.multiblock().form(candidate, Direction.NORTH),
            "Fixture forms");
        validations[0] = 0;
        entity.serverTick();
        require(validations[0] == 1, "Controller validates once per tick");
        var socket = level.getBlockEntity(socket(entity));
        var items = MultiblockPorts.items(socket, Direction.NORTH);
        var fluid = MultiblockPorts.fluids(socket, Direction.NORTH);
        var energy = MultiblockPorts.energy(socket, Direction.NORTH);
        for (int i = 0; i < 10; i++) {
            require(entity.multiblock().ready(),
                "Ready snapshot remains readable");
            items.getSlots();
            items.getStackInSlot(0);
            items.insertItem(0, new ItemStack(Items.IRON_INGOT), true);
            fluid.fill(new FluidStack(Fluids.WATER, 10),
                IFluidHandler.FluidAction.SIMULATE);
            energy.receiveEnergy(10, true);
        }
        require(validations[0] == 1,
            "Capability reads/transfers never revalidate");
        // A broken reciprocal link is discovered by the lifecycle, not a
        // neighboring handler.
        ((zone.moddev.mc.lib.machine.multiblock.LinkedMember) level
            .getBlockEntity(pos.east())).memberLink(null);
        items.getSlots();
        items.getStackInSlot(0);
        require(level.getBlockEntity(pos) == entity,
            "Capability query cannot dismantle the owner");
        entity.serverTick();
        require(level.getBlockState(pos).isAir(),
            "Lifecycle detects invalid membership");
        require(items.getSlots() == 0 && energy.receiveEnergy(10, false) == 0,
            "Retained handlers become inert after teardown");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void explosionDropsOnePackagedMachineAndItsContents(
        GameTestHelper helper) {
        var entity = place(helper, Direction.NORTH);
        var level = helper.getLevel();
        entity.items.insertItem(0, new ItemStack(Items.DIAMOND, 3), false);
        var pos = socket(entity);
        var state = level.getBlockState(pos);
        var explosion = new net.minecraft.world.level.Explosion(level, null,
            pos.getX(), pos.getY(), pos.getZ(), 1, false,
            net.minecraft.world.level.Explosion.BlockInteraction.DESTROY);
        state.getBlock().onBlockExploded(state, level, pos, explosion);
        var drops = level.getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class,
            new net.minecraft.world.phys.AABB(origin(helper)).inflate(3));
        require(
            drops.stream()
                .filter(e -> e.getItem().is(MultiblockDemo.ITEM.get()))
                .mapToInt(e -> e.getItem().getCount()).sum() == 1,
            "Explosion awards one machine item");
        require(
            drops.stream().filter(e -> e.getItem().is(Items.DIAMOND))
                .mapToInt(e -> e.getItem().getCount()).sum() == 3,
            "Explosion drops contents once");
        require(level.getBlockState(origin(helper)).isAir(),
            "Explosion dismantles the structure");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void unavailableChunkSuspendsWithoutLoadingAndResumesSameIdentity(
        GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = origin(helper);
        BlockPos offset = null;
        for (int x = -128; x <= 128 && offset == null; x += 16)
            for (int z = -128; z <= 128; z += 16)
                if (!level.hasChunkAt(pos.offset(x, 0, z))) {
                    offset = new BlockPos(x, 0, z);
                    break;
                }
        require(offset != null,
            "Fixture has an unloaded neighbor within bounded extent");
        var partPos = pos.offset(offset);
        var candidate = new ResolvedStructure(
            List.of(
                new StructureMember("controller", "controller", BlockPos.ZERO,
                    MultiblockDemo.CONTROLLER.get().defaultBlockState()),
                new StructureMember("socket", "socket", offset,
                    MultiblockDemo.PART.get().defaultBlockState())),
            List.of(), Map.of());
        var definition = new zone.moddev.mc.lib.machine.multiblock.MultiblockDefinition(
            net.minecraft.resources.ResourceLocation
                .parse("mmdlib_companion:chunk_fixture"),
            1, new zone.moddev.mc.lib.machine.multiblock.FixedLayout(candidate),
            16, 128,
            zone.moddev.mc.lib.machine.multiblock.MultiblockDefinition.Dismantling.REMOVE_OWNED,
            Map.of(), List.of());
        level.setBlock(pos, MultiblockDemo.CONTROLLER.get().defaultBlockState(),
            3);
        var entity = new MultiblockDemoEntity(pos, level.getBlockState(pos),
            definition);
        level.setBlockEntity(entity);
        require(entity.multiblock().stage(candidate, Direction.NORTH),
            "Manifest staged");
        var saved = entity.multiblock().save();
        saved.putBoolean("committed", true);
        entity.multiblock().load(saved, level.registryAccess());
        var identity = entity.multiblock().formation();
        entity.serverTick();
        require(
            entity.multiblock().status() == MultiblockInstance.Status.SUSPENDED,
            "Unloaded member suspends");
        require(!level.hasChunkAt(partPos),
            "Validation did not load the missing chunk");
        require(
            level.getBlockEntity(pos) == entity && entity.getOperations() == 0,
            "Owner and resources remain inactive");
        // The test explicitly supplies the returning chunk and part, as world
        // loading would.
        level.getChunkAt(partPos);
        level.setBlock(partPos, MultiblockDemo.PART.get().defaultBlockState(),
            3);
        ((zone.moddev.mc.lib.machine.multiblock.LinkedMember) level
            .getBlockEntity(partPos))
            .memberLink(entity.multiblock().memberLink("socket"));
        entity.serverTick();
        require(
            entity.multiblock().ready()
                && identity.equals(entity.multiblock().formation()),
            "Same formation resumes after verification");
        entity.multiblock().dismantle(MultiblockInstance.Cause.CREATIVE);
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void existingMembersUseSameLifecycleAndLeaveBlocksPolicy(
        GameTestHelper helper) {
        var base = MultiblockDemo.definition();
        var definition = new zone.moddev.mc.lib.machine.multiblock.MultiblockDefinition(
            base.id(), base.version(), base.resolver(), base.maxMembers(),
            base.maxExtent(),
            zone.moddev.mc.lib.machine.multiblock.MultiblockDefinition.Dismantling.LEAVE_BLOCKS,
            base.rules(), base.ports());
        var level = helper.getLevel();
        var pos = origin(helper);
        var candidate = ((zone.moddev.mc.lib.machine.multiblock.FixedLayout) base
            .resolver()).structure();
        for (var member : candidate.members())
            level.setBlock(pos.offset(member.offset()), member.state(), 3);
        var entity = new MultiblockDemoEntity(pos, level.getBlockState(pos),
            definition);
        level.setBlockEntity(entity);
        entity.items.insertItem(0, new ItemStack(Items.GOLD_INGOT, 4), false);
        require(entity.multiblock().form(candidate, Direction.NORTH),
            "Existing members form without placement");
        var oldIdentity = entity.multiblock().formation();
        entity.multiblock().dismantle(MultiblockInstance.Cause.INVALIDATED);
        for (var member : candidate.members()) {
            var memberPos = pos.offset(member.offset());
            require(level.getBlockState(memberPos).equals(member.state()),
                "Player-built member stays");
            require(MultiblockMembers.INSTANCE.link(level, memberPos) == null,
                "Claim released");
        }
        require(entity.items.getStackInSlot(0).getCount() == 4,
            "Controller contents remain");
        require(entity.multiblock().form(candidate, Direction.NORTH),
            "Explicit reformation succeeds");
        require(!oldIdentity.equals(entity.multiblock().formation()),
            "Reformation has a new identity");
        var player = helper
            .makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var state = level.getBlockState(pos);
        state.getBlock().playerWillDestroy(level, pos, state, player);
        level.removeBlock(pos, false);
        var drops = level.getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class,
            new net.minecraft.world.phys.AABB(pos).inflate(3));
        require(
            drops.stream().filter(e -> e.getItem().is(Items.GOLD_INGOT))
                .mapToInt(e -> e.getItem().getCount()).sum() == 4,
            "Breaking a leave-blocks controller drops its retained contents exactly once");
        require(
            drops.stream()
                .noneMatch(e -> e.getItem().is(MultiblockDemo.ITEM.get())),
            "A player-built structure does not award a packaged machine");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void interruptedPlacementCleansUpWithoutReward(
        GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = origin(helper);
        level.setBlock(pos, MultiblockDemo.CONTROLLER.get().defaultBlockState(),
            3);
        var entity = (MultiblockDemoEntity) level.getBlockEntity(pos);
        var candidate = ((zone.moddev.mc.lib.machine.multiblock.FixedLayout) MultiblockDemo
            .definition().resolver()).structure();
        require(entity.multiblock().stage(candidate, Direction.NORTH),
            "Pending placement staged");
        level.setBlock(pos.east(),
            MultiblockDemo.PART.get().defaultBlockState(), 3);
        ((zone.moddev.mc.lib.machine.multiblock.LinkedMember) level
            .getBlockEntity(pos.east()))
            .memberLink(entity.multiblock().memberLink("belt"));
        var saved = entity.saveWithFullMetadata(level.registryAccess());
        var restored = (MultiblockDemoEntity) BlockEntity.loadStatic(pos,
            entity.getBlockState(), saved, level.registryAccess());
        level.setBlockEntity(restored);
        restored.serverTick();
        require(
            level.getBlockState(pos).isAir()
                && level.getBlockState(pos.east()).isAir(),
            "Partial owned placement cleaned up");
        require(
            level
                .getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(2))
                .isEmpty(),
            "Uncommitted placement creates no rewards");
        helper.succeed();
    }
    private static BlockPos origin(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(2, 2, 2));
    }
    private static BlockPlaceContext context(GameTestHelper helper,
        Direction facing) {
        var player = helper
            .makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var pos = origin(helper);
        player.setPos(pos.getX() + .5, pos.getY() + 4, pos.getZ() + .5);
        player.setYRot(facing.getOpposite().toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(MultiblockDemo.ITEM.get(), 2));
        return new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
            player.getMainHandItem(),
            new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }
    private static MultiblockDemoEntity place(GameTestHelper helper,
        Direction facing) {
        var result = MultiblockPlacement.place(context(helper, facing),
            MultiblockDemo.definition());
        require(result.success(), "Placement: " + result.diagnostic());
        return (MultiblockDemoEntity) helper.getLevel()
            .getBlockEntity(origin(helper));
    }
    private static BlockPos socket(MultiblockDemoEntity entity) {
        return entity.multiblock().transform().position(new BlockPos(2, 0, 0));
    }
    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    @GameTest(template = "empty")
    public static void wholePlacementRotatesLinksAndConsumesOneItem(
        GameTestHelper helper) {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var context = context(helper, facing);
            var use = new UseOnContext(context.getPlayer(),
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(origin(helper)),
                    Direction.UP, origin(helper), false));
            require(
                net.neoforged.neoforge.common.CommonHooks
                    .onPlaceItemIntoWorld(use).consumesAction(),
                "Item placement succeeds");
            require(context.getPlayer().getMainHandItem().getCount() == 1,
                "Exactly one item consumed");
            var entity = (MultiblockDemoEntity) helper.getLevel()
                .getBlockEntity(origin(helper));
            require(entity.multiblock().ready(), "Machine is ready");
            require(entity.multiblock().transform().facing() == facing,
                "Facing follows placement");
            require(
                MultiblockMembers.resolve(helper.getLevel(),
                    socket(entity)) == entity.multiblock(),
                "Part resolves reciprocally");
            entity.multiblock().dismantle(MultiblockInstance.Cause.CREATIVE);
            require(helper.getLevel().getBlockState(origin(helper)).isAir(),
                "Controller removed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void obstructionAndCancelledPlacementRollBack(
        GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = origin(helper);
        level.setBlock(pos.east(), Blocks.STONE.defaultBlockState(), 3);
        require(!MultiblockPlacement.place(context(helper, Direction.NORTH),
            MultiblockDemo.definition()).success(), "Obstruction refused");
        require(level.getBlockState(pos).isAir(), "Preflight writes nothing");
        level.removeBlock(pos.east(), false);
        Consumer<BlockEvent.EntityMultiPlaceEvent> cancel = event -> {
            if (event.getPos().equals(pos))
                event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            var context = context(helper, Direction.NORTH);
            require(!MultiblockPlacement
                .place(context, MultiblockDemo.definition()).success(),
                "Cancelled event refused");
            require(context.getItemInHand().getCount() == 2,
                "Failure consumes nothing");
            for (int x = 0; x < 3; x++)
                require(level.getBlockState(pos.east(x)).isAir(),
                    "Cancelled writes restored");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollbackPreservesConcurrentReplacement(
        GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = origin(helper);
        Consumer<BlockEvent.EntityMultiPlaceEvent> replace = event -> {
            if (event.getPos().equals(pos))
                level.setBlock(pos.east(),
                    Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        };
        NeoForge.EVENT_BUS.addListener(replace);
        try {
            require(
                !MultiblockPlacement.place(context(helper, Direction.NORTH),
                    MultiblockDemo.definition()).success(),
                "Modified plan refused");
            require(level.getBlockState(pos.east()).is(Blocks.DIAMOND_BLOCK),
                "Unrelated replacement survives rollback");
            require(
                level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.east(2)).isAir(),
                "Owned partial parts removed");
        } finally {
            NeoForge.EVENT_BUS.unregister(replace);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cachedPortsSuspendResumeAndNeverRetarget(
        GameTestHelper helper) {
        var entity = place(helper, Direction.NORTH);
        var level = helper.getLevel();
        var socket = level.getBlockEntity(socket(entity));
        var energy = MultiblockPorts.energy(socket, Direction.NORTH);
        var items = MultiblockPorts.items(socket, Direction.NORTH);
        var fluid = MultiblockPorts.fluids(socket, Direction.NORTH);
        require(energy != null && items != null && fluid != null,
            "All three resource adapters exposed");
        require(MultiblockPorts.energy(socket, null) == null,
            "Unsided access refused by policy");
        require(energy.receiveEnergy(500, false) == 500, "Energy forwards");
        require(items.insertItem(0, new ItemStack(Items.IRON_INGOT, 3), false)
            .isEmpty(), "Items forward");
        require(fluid.fill(new FluidStack(Fluids.WATER, 1000),
            IFluidHandler.FluidAction.EXECUTE) == 1000, "Fluid forwards");
        var blocker = origin(helper).east().above();
        level.setBlock(blocker, Blocks.STONE.defaultBlockState(), 3);
        int operations = entity.getOperations();
        entity.serverTick();
        require(
            entity.getOperations() == operations && entity.multiblock()
                .status() == MultiblockInstance.Status.SUSPENDED,
            "Custom tick hooks suspended");
        require(
            energy.extractEnergy(100, false) == 0
                && items.extractItem(0, 1, false).isEmpty() && fluid
                    .drain(100, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
            "Cached handlers become inert");
        level.removeBlock(blocker, false);
        entity.serverTick();
        require(
            entity.getOperations() == operations + 1
                && energy.extractEnergy(1, false) == 1,
            "Same formation resumes");
        entity.multiblock().dismantle(MultiblockInstance.Cause.CREATIVE);
        var replacement = place(helper, Direction.NORTH);
        replacement.energy.insert(100, false);
        require(energy.extractEnergy(100, false) == 0,
            "Old energy wrapper cannot retarget");
        require(items.insertItem(0, new ItemStack(Items.DIAMOND), false)
            .is(Items.DIAMOND), "Old item wrapper refuses insertion");
        require(
            fluid.fill(new FluidStack(Fluids.WATER, 1),
                IFluidHandler.FluidAction.EXECUTE) == 0,
            "Old fluid wrapper refuses filling");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void manifestReloadAndIncompatibleDefinitionPreserveStorage(
        GameTestHelper helper) {
        var entity = place(helper, Direction.EAST);
        var level = helper.getLevel();
        entity.items.insertItem(0, new ItemStack(Items.GOLD_INGOT, 7), false);
        entity.energy.insert(700, false);
        var formation = entity.multiblock().formation();
        var saved = entity.saveWithFullMetadata(level.registryAccess());
        var restored = (MultiblockDemoEntity) BlockEntity.loadStatic(
            entity.getBlockPos(), entity.getBlockState(), saved,
            level.registryAccess());
        level.setBlockEntity(restored);
        require(
            restored.multiblock()
                .status() == MultiblockInstance.Status.SUSPENDED,
            "Saved ready state is not trusted");
        restored.serverTick();
        require(
            restored.multiblock().ready()
                && formation.equals(restored.multiblock().formation()),
            "Reload verifies existing identity");
        saved.getCompound("mmdlib_machine").getCompound("multiblock")
            .putInt("version", 999);
        var incompatible = (MultiblockDemoEntity) BlockEntity.loadStatic(
            entity.getBlockPos(), entity.getBlockState(), saved,
            level.registryAccess());
        level.setBlockEntity(incompatible);
        incompatible.serverTick();
        require(!incompatible.multiblock().ready(),
            "Changed definition is inactive");
        require(incompatible.items.getStackInSlot(0).getCount() == 7
            && incompatible.energy.stored() == 700, "Resources retained");
        require(incompatible.saveWithFullMetadata(level.registryAccess())
            .getCompound("mmdlib_machine").getCompound("multiblock")
            .getInt("version") == 999, "Unknown manifest preserved");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingPartDropsOnceAndPreservesReplacement(
        GameTestHelper helper) {
        var entity = place(helper, Direction.NORTH);
        var level = helper.getLevel();
        entity.items.insertItem(0, new ItemStack(Items.DIAMOND, 3), false);
        var part = socket(entity);
        entity.multiblock().dismantle(MultiblockInstance.Cause.SURVIVAL);
        entity.multiblock().dismantle(MultiblockInstance.Cause.SURVIVAL);
        var drops = level.getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class,
            new net.minecraft.world.phys.AABB(origin(helper)).inflate(3));
        require(
            drops.stream()
                .filter(e -> e.getItem().is(MultiblockDemo.ITEM.get()))
                .mapToInt(e -> e.getItem().getCount()).sum() == 1,
            "One machine item");
        require(
            drops.stream().filter(e -> e.getItem().is(Items.DIAMOND))
                .mapToInt(e -> e.getItem().getCount()).sum() == 3,
            "Contents released once");
        require(level.getBlockState(part).isAir(), "Part removed");
        var next = place(helper, Direction.NORTH);
        level.setBlock(socket(next), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        next.serverTick();
        require(level.getBlockState(part).is(Blocks.GOLD_BLOCK),
            "Replacement survives invalidation");
        require(level.getBlockState(origin(helper)).isAir(),
            "Remaining owned controller removed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void remoteMenuAnchorUsesPartReachAndClosesOnLoss(
        GameTestHelper helper) {
        var entity = place(helper, Direction.NORTH);
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var pos = origin(helper);
        player.setPos(pos.getX() + 10, pos.getY(), pos.getZ() + .5);
        var menu = new MachineMenu(31, player.getInventory(), entity, 1L,
            socket(entity));
        require(!player.canInteractWithBlock(pos, 4.0),
            "Fixture player is beyond controller reach");
        require(menu.stillValid(player), "Verified part supplies reach");
        var forged = new MachineMenu(32, player.getInventory(), entity, 2L,
            player.blockPosition());
        require(!forged.stillValid(player), "Arbitrary nearby anchor refused");
        helper.getLevel().removeBlock(socket(entity), false);
        require(!menu.stillValid(player), "Removed member closes menu");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void definitionRejectsDuplicateAndOutOfBoundsMembers(
        GameTestHelper helper) {
        var state = MultiblockDemo.CONTROLLER.get().defaultBlockState();
        try {
            new ResolvedStructure(List.of(
                new StructureMember("controller", "controller", BlockPos.ZERO,
                    state),
                new StructureMember("duplicate", "belt", BlockPos.ZERO, state)),
                List.of(), Map.of());
            throw new AssertionError("Duplicate position accepted");
        } catch (IllegalArgumentException expected) {
        }
        var candidate = new ResolvedStructure(List.of(
            new StructureMember("controller", "controller", BlockPos.ZERO,
                state),
            new StructureMember("far", "belt", new BlockPos(129, 0, 0), state)),
            List.of(), Map.of());
        try {
            MultiblockDemo.definition().validate(candidate);
            throw new AssertionError("Invalid manifest accepted");
        } catch (IllegalArgumentException expected) {
        }
        helper.succeed();
    }
}
