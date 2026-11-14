package zone.moddev.mc.companion.test;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.lib.machine.feature.PortableData;
import zone.moddev.mc.lib.machine.feature.PortableStoredProcessor;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

@GameTestHolder("mmdlib_companion")
@PrefixGameTestTemplate(false)
public final class PortableMachineTests {
    @GameTest(template = "empty")
    public static void shapeTransitionsAndConditionsUseTheCommittedProcessorState(
        GameTestHelper h) {
        var machine = occupied(h);
        var registries = h.getLevel().registryAccess();
        var id = ResourceLocation.parse("mmdlib_companion:test/portable");
        var copy = (DefinedMachineEntity) MachineContent.entityType(id)
            .create(machine.getBlockPos(), machine.getBlockState());
        copy.loadWithComponents(machine.getUpdateTag(registries), registries);
        h.assertTrue(
            copy.shape(Direction.NORTH).bounds().getYsize() == 3.0 / 16,
            "Client feature state selects the occupied plate");
        machine.portable().placed(new ItemStack(MachineContent.item(id)));
        h.assertTrue(machine.shape(Direction.NORTH).bounds().getYsize() == 1
            && !machine.features.condition("$processors.portable.contents > 0"),
            "Shared shape and button conditions see the completed update");
        copy.loadWithComponents(machine.getUpdateTag(registries), registries);
        h.assertTrue(copy.shape(Direction.NORTH).bounds().getYsize() == 1,
            "The same update carries the new state and client bounds");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void badSavedProcessorFieldDoesNotDiscardBlockEntityInventory(
        GameTestHelper h) {
        var machine = occupied(h);
        var registries = h.getLevel().registryAccess();
        var saved = machine.saveWithFullMetadata(registries);
        saved.getCompound("features").getCompound("portable").putInt("contents",
            999);
        var restored = net.minecraft.world.level.block.entity.BlockEntity
            .loadStatic(machine.getBlockPos(), machine.getBlockState(), saved,
                registries);
        h.assertTrue(restored instanceof DefinedMachineEntity,
            "Invalid processor data cannot discard the whole block entity during vanilla loading");
        var copy = (DefinedMachineEntity) restored;
        h.assertTrue(
            copy.items.getStackInSlot(0).getCount() == 3 && copy.items
                .getStackInSlot(0).is(net.minecraft.world.item.Items.DIAMOND),
            "Unrelated saved inventory is retained");
        h.assertTrue(
            copy.features.findProcessor("portable", Payload.class).orElseThrow()
                .state().getInt("contents") == 0,
            "Only the rejected variable falls back to its default");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeBreakingOccupiedMachinesDoesNotPickUpPayload(
        GameTestHelper h) {
        creativeBreak(h, true);
    }

    @GameTest(template = "empty")
    public static void creativeBreakingEmptyMachinesDoesNotCreateItems(
        GameTestHelper h) {
        creativeBreak(h, false);
    }

    private static void creativeBreak(GameTestHelper h, boolean occupied) {
        var machine = occupied(h);
        var pos = machine.getBlockPos();
        var item = MachineContent
            .item(ResourceLocation.parse("mmdlib_companion:test/portable"));
        if (!occupied)
            machine.portable().placed(new ItemStack(item));
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory
            .get(h.getLevel(), new com.mojang.authlib.GameProfile(
                java.util.UUID.randomUUID(), "portable-creative"));
        player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(player.gameMode.destroyBlock(pos),
            "Creative breaking removes the machine");
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class,
            new AABB(pos).inflate(2));
        h.assertTrue(
            drops.stream().noneMatch(drop -> drop.getItem().is(item))
                && player.getInventory().items.stream()
                    .noneMatch(stack -> stack.is(item)),
            "Creative breaking never substitutes for UI or tagged-tool pickup");
        h.assertTrue(
            drops.stream().map(ItemEntity::getItem)
                .filter(
                    stack -> stack.is(net.minecraft.world.item.Items.DIAMOND))
                .mapToInt(ItemStack::getCount).sum() == 3,
            "Ordinary inventory contents still drop");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void conditionalShapeDoesNotOccludeNeighbors(
        GameTestHelper h) {
        var machine = occupied(h);
        h.assertTrue(!machine.getBlockState().canOcclude(),
            "Entity-dependent shapes cannot use the blockstate occlusion cache");
        h.assertTrue(
            machine.getBlockState()
                .getShape(h.getLevel(), machine.getBlockPos()).bounds()
                .getYsize() == 3.0 / 16,
            "Disabling occlusion preserves the declared physical shape");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pickupHonorsAdventureAndCanceledBreakEvents(
        GameTestHelper h) {
        var machine = occupied(h);
        var pos = machine.getBlockPos();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory
            .get(h.getLevel(), new com.mojang.authlib.GameProfile(
                java.util.UUID.randomUUID(), "portable-protect"));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2);
        var processor = machine.screenFeatures().findProcessor("portable")
            .orElseThrow();
        player.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);
        h.assertTrue(
            !processor.invoke(PortableStoredProcessor.PICKUP, player)
                .accepted(),
            "Adventure players cannot pick up machines through UI actions");
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(net.minecraft.world.item.Items.STICK));
        var events = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<BlockEvent.BreakEvent> listener = event -> {
            if (event.getPlayer() == player && event.getPos().equals(pos)) {
                events.incrementAndGet();
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            h.assertTrue(!processor
                .invoke(PortableStoredProcessor.PICKUP, player).accepted(),
                "Protected pickup actions are refused");
            player.setShiftKeyDown(true);
            player.gameMode.useItemOn(player, h.getLevel(),
                player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos,
                    false));
            h.assertTrue(events.get() == 2,
                "Both UI and tagged-tool pickup fire BreakEvent once");
            h.assertTrue(
                h.getLevel().getBlockEntity(pos) == machine
                    && machine.items.getStackInSlot(0).getCount() == 3
                    && h.getLevel()
                        .getEntitiesOfClass(ItemEntity.class,
                            new AABB(pos).inflate(2))
                        .isEmpty(),
                "Denied pickup changes neither block nor inventory ownership");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        h.assertTrue(
            processor.invoke(PortableStoredProcessor.PICKUP, player).accepted(),
            "Pickup can succeed after protection is lifted");
        h.assertTrue(
            player.getInventory().items.stream()
                .anyMatch(stack -> PortableData.read(stack, "portable",
                    "contents", Codec.INT, 0) == 7),
            "Denied attempts did not consume the payload");
        h.succeed();
    }

    private static DefinedMachineEntity occupied(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var id = ResourceLocation.parse("mmdlib_companion:test/portable");
        h.getLevel().setBlock(pos, MachineContent.block(id).defaultBlockState(),
            3);
        var machine = (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
        var stack = new ItemStack(MachineContent.item(id));
        PortableData.write(stack, "portable", "contents", Codec.INT, 7);
        machine.portable().placed(stack);
        machine.items.setStackInSlot(0,
            new ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        return machine;
    }

    @GameTest(template = "empty")
    public static void ordinaryDestructionDropsEmptyMachineAndInventory(
        GameTestHelper h) {
        var machine = occupied(h);
        var pos = machine.getBlockPos();
        h.getLevel().destroyBlock(pos, true);
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class,
            new AABB(pos).inflate(2));
        var item = MachineContent
            .item(ResourceLocation.parse("mmdlib_companion:test/portable"));
        var machines = drops.stream().map(ItemEntity::getItem)
            .filter(stack -> stack.is(item)).toList();
        h.assertTrue(
            machines.size() == 1
                && PortableData.read(machines.getFirst(), "portable").isEmpty(),
            "Normal loot drops one empty machine without portable processor contents");
        h.assertTrue(
            drops.stream().map(ItemEntity::getItem)
                .filter(
                    stack -> stack.is(net.minecraft.world.item.Items.DIAMOND))
                .mapToInt(ItemStack::getCount).sum() == 3,
            "Inventory contents still drop");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wrongToolDropsInventoryButNoMachine(GameTestHelper h) {
        var machine = occupied(h);
        var pos = machine.getBlockPos();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory
            .get(h.getLevel(), new com.mojang.authlib.GameProfile(
                java.util.UUID.randomUUID(), "portable-break"));
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(player.gameMode.destroyBlock(pos),
            "Survival break removes the machine");
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class,
            new AABB(pos).inflate(2));
        var item = MachineContent
            .item(ResourceLocation.parse("mmdlib_companion:test/portable"));
        h.assertTrue(drops.stream().noneMatch(drop -> drop.getItem().is(item)),
            "Wrong tool cannot bypass the machine's tool requirement");
        h.assertTrue(
            drops.stream().map(ItemEntity::getItem)
                .filter(
                    stack -> stack.is(net.minecraft.world.item.Items.DIAMOND))
                .mapToInt(ItemStack::getCount).sum() == 3,
            "Wrong tool still releases inventory contents");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void taggedToolPreservesPayloadAndEmptyHandCannotPickUp(
        GameTestHelper h) {
        var machine = occupied(h);
        var player = h.makeMockServerPlayerInLevel();
        var pos = machine.getBlockPos();
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos,
            false);
        player.gameMode.useItemOn(player, h.getLevel(),
            player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        h.assertTrue(h.getLevel().getBlockEntity(pos) == machine,
            "Empty-hand secondary use cannot pick up payloads");
        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(net.minecraft.world.item.Items.STICK));
        player.gameMode.useItemOn(player, h.getLevel(),
            player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        h.assertTrue(h.getLevel().getBlockState(pos).isAir(),
            "Tagged tool picks up the machine");
        var carried = player.getInventory().items.stream()
            .filter(stack -> PortableData.read(stack, "portable", "contents",
                Codec.INT, 0) == 7)
            .toList();
        h.assertTrue(carried.size() == 1 && carried.getFirst().getCount() == 1,
            "Tagged pickup transfers portable ownership exactly once");
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class,
            new AABB(pos).inflate(2));
        h.assertTrue(
            drops.stream().map(ItemEntity::getItem)
                .filter(
                    stack -> stack.is(net.minecraft.world.item.Items.DIAMOND))
                .mapToInt(ItemStack::getCount).sum() == 3,
            "Pickup preserves inventory drops");
        h.succeed();
    }

    public static final class Payload extends PortableStoredProcessor {
        public Payload() {
            variables.integer("contents", 0, 100, 0).visual().portable()
                .define();
        }

        public boolean canStart(ProcessorState state,
            ProcessorMachine machine) {
            return false;
        }

        public ProcessState process(ProcessorState state,
            ProcessorMachine machine, int deltaTicks) {
            return ProcessState.IDLE;
        }
    }

    @GameTest(template = "empty")
    public static void creativePlacementConsumesCarriedContentExactlyOnce(
        GameTestHelper h) {
        place(h, 1, 7, 0);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void creativePlacementKeepsOnlyTheUnplacedPortableItems(
        GameTestHelper h) {
        place(h, 3, 7, 2);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void creativePlacementKeepsEmptyMachinesRenewable(
        GameTestHelper h) {
        place(h, 3, 0, 3);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedPortablePlacementUsesDefaultWithoutThrowing(
        GameTestHelper h) {
        place(h, 1, -1, 0);
        h.succeed();
    }

    private static void place(GameTestHelper h, int count, int contents,
        int remaining) {
        place(h, count, contents, remaining, false);
    }

    @GameTest(template = "empty")
    public static void canceledCreativePlacementPreservesHandWithoutDroppingCopies(
        GameTestHelper h) {
        place(h, 3, 7, 3, true);
        h.succeed();
    }

    private static void place(GameTestHelper h, int count, int contents,
        int remaining, boolean cancel) {
        var player = h.makeMockServerPlayerInLevel();
        player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(Vec3.atCenterOf(pos.offset(3, 0, 0)));
        h.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        var item = MachineContent
            .item(ResourceLocation.parse("mmdlib_companion:test/portable"));
        var stack = new ItemStack(item, count);
        var name = net.minecraft.network.chat.Component
            .literal("Carried machine");
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            name);
        stack.set(
            net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE,
            true);
        PortableData.write(stack, "portable", "contents", Codec.INT, contents);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP,
            pos.below(), false);
        // Exercise the outer creative count restoration, not just Item.place.
        java.util.function.Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (cancel && event.getEntity() == player
                && event.getPos().equals(pos))
                event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            var result = player.gameMode.useItemOn(player, h.getLevel(), stack,
                InteractionHand.MAIN_HAND, hit);
            h.assertTrue(result.consumesAction() != cancel,
                "Placement respects the event result");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        h.assertTrue(player.getMainHandItem().getCount() == remaining,
            "Creative placement consumes exactly the transferred item");
        if (cancel) {
            h.assertTrue(h.getLevel().getBlockState(pos).isAir(),
                "Canceled placement restores the world");
            h.assertTrue(
                PortableData.read(player.getMainHandItem(), "portable",
                    "contents", Codec.INT, -1) == contents,
                "Canceled placement keeps the original content");
            h.assertTrue(
                h.getLevel()
                    .getEntitiesOfClass(ItemEntity.class,
                        new AABB(pos).inflate(2))
                    .isEmpty(),
                "Rollback cannot drop a duplicate portable payload");
            return;
        }
        var machine = (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
        h.assertTrue(machine != null, "Placement created the machine");
        var recovered = machine.portable().take();
        h.assertTrue(
            PortableData.read(recovered, "portable", "contents", Codec.INT,
                -1) == (contents < 0 ? 0 : contents),
            "Valid payloads survive placement; invalid values use their default");
        h.assertTrue(name
            .equals(recovered
                .get(net.minecraft.core.component.DataComponents.CUSTOM_NAME))
            && Boolean.TRUE.equals(recovered.get(
                net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),
            "Placement and pickup preserve names and other block-entity components");
        h.assertTrue(machine.portable().take().isEmpty(),
            "Transferred content cannot be taken twice");
        if (remaining > 0)
            h.assertTrue(
                PortableData.read(player.getMainHandItem(), "portable",
                    "contents", Codec.INT, -1) == contents,
                "Unplaced items retain their content");
    }
}
