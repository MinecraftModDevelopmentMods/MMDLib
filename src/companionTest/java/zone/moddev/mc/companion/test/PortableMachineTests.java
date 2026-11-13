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
    public static final class Payload extends PortableStoredProcessor {
        public Payload() {
            variables.integer("contents", 0, 100, 0).portable().define();
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
        h.assertTrue(PortableData.read(recovered, "portable", "contents",
            Codec.INT, -1) == contents, "Placed content is preserved");
        h.assertTrue(machine.portable().take().isEmpty(),
            "Transferred content cannot be taken twice");
        if (remaining > 0)
            h.assertTrue(
                PortableData.read(player.getMainHandItem(), "portable",
                    "contents", Codec.INT, -1) == contents,
                "Unplaced items retain their content");
    }
}
