package zone.moddev.mc.companion.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import zone.moddev.mc.companion.MultiblockDemo;
import zone.moddev.mc.companion.MultiblockDemoEntity;
import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;
import zone.moddev.mc.lib.machine.multiblock.MultiblockInstance;

/**
 * Focused real-client coverage of member sync, remote menu opening and world
 * teardown.
 */
@EventBusSubscriber(modid = "mmdlib_companion", value = Dist.CLIENT)
public final class MultiblockClientSmoke {
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static final long DEADLINE = System.currentTimeMillis() + 240000;
    private static boolean started, placed, requested, finished;
    private static volatile boolean removed;
    private static int frames;
    private static int ticks;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mmdlib.multiblockSmoke") || finished)
            return;
        var mc = Minecraft.getInstance();
        try {
            if (System.currentTimeMillis() > DEADLINE)
                throw new AssertionError("Timed out: " + mc.screen);
            if (!started && mc.screen instanceof TitleScreen) {
                started = true;
                var settings = new LevelSettings("Multiblock smoke",
                    GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                    new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(
                    "multiblock-" + System.currentTimeMillis(), settings,
                    new WorldOptions(1, false, false),
                    registries -> registries
                        .registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions(),
                    mc.screen);
            }
            if (mc.player == null || mc.level == null)
                return;
            if (++ticks % 200 == 0) {
                var entity = mc.level.getBlockEntity(POS);
                MMDLib.LOGGER.info(
                    "MULTIBLOCK_CLIENT_PROGRESS: placed={} requested={} screen={} controller={} formation={} pos={}",
                    placed, requested, mc.screen, entity,
                    entity instanceof MultiblockDemoEntity machine
                        ? machine.multiblock().formation()
                        : null,
                    mc.player.position());
            }
            var server = mc.getSingleplayerServer();
            if (!placed) {
                placed = true;
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayers()
                            .getFirst();
                        var level = player.serverLevel();
                        level.getChunkAt(POS);
                        for (int x = -5; x <= 12; x++)
                            for (int z = -5; z <= 5; z++)
                                level.setBlock(new BlockPos(x, 99, z),
                                    Blocks.STONE.defaultBlockState(), 3);
                        player.teleportTo(1.5, 100, 4.5);
                        player.setYRot(0);
                        player.setGameMode(GameType.SURVIVAL);
                        player.setItemInHand(InteractionHand.MAIN_HAND,
                            new ItemStack(MultiblockDemo.ITEM.get(), 2));
                        var use = new UseOnContext(player,
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(POS),
                                Direction.UP, POS, false));
                        if (!net.neoforged.neoforge.common.CommonHooks
                            .onPlaceItemIntoWorld(use).consumesAction()
                            || player.getMainHandItem().getCount() != 1)
                            throw new AssertionError(
                                "Real survival placement did not consume exactly one item");
                        var entity = (MultiblockDemoEntity) level
                            .getBlockEntity(POS);
                        entity.items.insertItem(0,
                            new ItemStack(Items.IRON_INGOT, 3), false);
                        entity.energy.insert(7000, false);
                        MMDLib.LOGGER.info("MULTIBLOCK_CLIENT_PLACED: {}",
                            entity.multiblock().formation());
                    } catch (Throwable failure) {
                        fail(failure);
                    }
                });
            }
            if (!requested
                && mc.level
                    .getBlockEntity(POS) instanceof MultiblockDemoEntity entity
                && entity.multiblock().formation() != null
                && mc.level.getBlockEntity(POS.east(2)) != null) {
                requested = true;
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayers()
                            .getFirst();
                        player.teleportTo(10, 100, .5);
                        var machine = (MultiblockDemoEntity) player
                            .serverLevel().getBlockEntity(POS);
                        if (machine.items.getStackInSlot(0).getCount() != 3)
                            throw new AssertionError(
                                "Server fixture inventory was lost: "
                                    + machine.items.getStackInSlot(0));
                        if (player.canInteractWithBlock(POS, 4.0))
                            throw new AssertionError(
                                "Player still reaches controller");
                        if (!ScreenRuntime.open(player,
                            player.serverLevel().getBlockEntity(POS),
                            POS.east(2)))
                            throw new AssertionError("Remote menu refused");
                    } catch (Throwable failure) {
                        fail(failure);
                    }
                });
            }
            if (removed && mc.level.getBlockState(POS).isAir()
                && mc.level.getBlockState(POS.east()).isAir()) {
                finished = true;
                MMDLib.LOGGER.info(
                    "MULTIBLOCK_CLIENT_PASS: survival placement, part sync, remote menu and teardown");
                mc.stop();
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    @SubscribeEvent
    public static void rendered(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("mmdlib.multiblockSmoke") || finished
            || removed)
            return;
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof MachineScreen screen)
            || !screen.getMenu().bound.screen().id()
                .equals(MultiblockDemo.definition().id()))
            return;
        try {
            if (++frames < 30)
                return;
            if (!screen.getMenu().stillValid(mc.player))
                throw new AssertionError(
                    "Client member anchor not synchronized");
            var entity = (MultiblockDemoEntity) mc.level.getBlockEntity(POS);
            if (entity.multiblock().status() != MultiblockInstance.Status.READY)
                throw new AssertionError("Client structure state missing");
            var storage = screen.getMenu().nativeSlots().entries().stream()
                .filter(entry -> entry.element() != null && entry
                    .role() == zone.moddev.mc.lib.gui.runtime.slot.MenuSlots.Role.MACHINE
                    && entry.inventory().equals("items"))
                .findFirst().orElseThrow();
            if (!storage.slot().getItem().is(Items.IRON_INGOT)
                || storage.slot().getItem().getCount() != 3) {
                if (frames < 300)
                    return;
                throw new AssertionError(
                    "Controller inventory not synchronized: "
                        + storage.slot().getItem());
            }
            Screenshot.grab(mc.gameDirectory, "multiblock-menu.png",
                mc.getMainRenderTarget(), message -> {
                });
            removed = true;
            mc.player.closeContainer();
            mc.getSingleplayerServer().execute(() -> {
                try {
                    mc.getSingleplayerServer().overworld()
                        .removeBlock(POS.east(2), false);
                } catch (Throwable failure) {
                    fail(failure);
                }
            });
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void fail(Throwable failure) {
        finished = true;
        MMDLib.LOGGER.error("MULTIBLOCK_CLIENT_FAIL", failure);
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
    }
}
