package zone.moddev.mc.lib.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.common.runtime.RedstoneBlock;

/**
 * Opt-in real-client validation; injected only by
 * crafttweaker-smoke.init.gradle.
 */
@EventBusSubscriber(modid = "mmdlib", value = Dist.CLIENT)
public final class CraftTweakerClientSmoke {
    private static boolean started, requested, finished;
    private static volatile boolean ready;
    private static int ticks, frames, stage;
    private static final long DEADLINE = System.currentTimeMillis() + 240_000;
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static final String[] ITEMS = {"client_alloy_ingot",
        "client_alloy_pickaxe", "client_alloy_block", "client_gate",
        "client_widget"};

    private static ResourceLocation id(String path) {
        return ResourceLocation.parse("mmdlib:" + path);
    }
    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
    private static boolean present() {
        return ModList.get().isLoaded("crafttweaker");
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mmdlib.craftTweakerSmoke") || finished)
            return;
        var mc = Minecraft.getInstance();
        try {
            require(System.currentTimeMillis() < DEADLINE,
                "client smoke timed out: " + mc.screen);
            if (!started && mc.screen instanceof TitleScreen) {
                started = true;
                require(
                    BuiltInRegistries.ITEM
                        .containsKey(id("client_widget")) == present(),
                    "optional integration registry boundary");
                var settings = new LevelSettings("CT smoke", GameType.CREATIVE,
                    false, Difficulty.PEACEFUL, true, new GameRules(),
                    WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(
                    "ct-smoke-" + System.currentTimeMillis(), settings,
                    new WorldOptions(1, false, false),
                    r -> r.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions(),
                    mc.screen);
            }
            if (mc.player == null || mc.level == null)
                return;
            if (!requested) {
                requested = true;
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var player = mc.getSingleplayerServer().getPlayerList()
                            .getPlayers().getFirst();
                        var level = player.serverLevel();
                        level.setDayTime(6000);
                        for (int x = -6; x <= 6; x++)
                            for (int z = -2; z <= 8; z++)
                                level.setBlock(POS.offset(x, -1, z),
                                    net.minecraft.world.level.block.Blocks.STONE
                                        .defaultBlockState(),
                                    3);
                        if (present()) {
                            int x = -3;
                            for (var direction : Direction.Plane.HORIZONTAL) {
                                level.setBlock(POS.offset(x, 0, 0),
                                    BuiltInRegistries.BLOCK
                                        .get(id("client_gate"))
                                        .defaultBlockState().setValue(
                                            RedstoneBlock.FACING, direction),
                                    3);
                                x += 2;
                            }
                            level.setBlock(POS.offset(0, 0, -2),
                                BuiltInRegistries.BLOCK
                                    .get(id("client_alloy_block"))
                                    .defaultBlockState(),
                                3);
                        }
                        player.teleportTo(.5, 103, 7.5);
                        player.setYRot(180);
                        player.setXRot(22);
                        if (present()) {
                            checkRecipes(mc);
                            var server = mc.getSingleplayerServer();
                            server
                                .reloadResources(
                                    server.getPackRepository().getSelectedIds())
                                .thenRunAsync(() -> {
                                    try {
                                        checkRecipes(mc);
                                        MMDLib.LOGGER.info(
                                            "MMDLIB_CT_RECIPES_RELOAD_PASS");
                                        ready = true;
                                    } catch (Throwable error) {
                                        fail(error);
                                    }
                                }, server).exceptionally(error -> {
                                    fail(error);
                                    return null;
                                });
                        } else
                            ready = true;
                    } catch (Throwable error) {
                        fail(error);
                    }
                });
            }
            if (!ready || ++ticks < 100 || stage != 0)
                return;
            if (present()) {
                require(
                    new ItemStack(
                        BuiltInRegistries.ITEM.get(id("client_widget")))
                        .getHoverName().getString().equals("Named Test Widget"),
                    "English name override");
                require(
                    new ItemStack(
                        BuiltInRegistries.ITEM.get(id("client_alloy_ingot")))
                        .getHoverName().getString().equals("Amber Alloy Ingot"),
                    "material English name");
                for (String name : ITEMS) {
                    var model = mc.getItemRenderer().getModel(
                        new ItemStack(BuiltInRegistries.ITEM.get(id(name))),
                        mc.level, mc.player, 0);
                    require(model != mc.getModelManager().getMissingModel(),
                        "missing item model: " + name);
                    require(!model.getParticleIcon().contents().name().getPath()
                        .equals("missingno"), "missing texture: " + name);
                }
                var block = BuiltInRegistries.BLOCK.get(id("client_gate"));
                for (var direction : Direction.Plane.HORIZONTAL) {
                    var state = block.defaultBlockState()
                        .setValue(RedstoneBlock.FACING, direction);
                    var model = mc.getBlockRenderer().getBlockModel(state);
                    require(
                        model.getQuads(state, direction, RandomSource.create(1))
                            .stream()
                            .anyMatch(q -> q.getSprite().contents().name()
                                .equals(id("block/client_gate_front"))),
                        "front model rotation: " + direction);
                }
            }
            mc.options.hideGui = true;
            mc.player.setYRot(180);
            mc.player.setXRot(22);
            mc.setScreen(null);
            stage = 1;
            frames = 0;
        } catch (Throwable error) {
            fail(error);
        }
    }

    private static void checkRecipes(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        var manager = server.getRecipeManager();
        var recipe = manager
            .byKey(ResourceLocation.parse("crafttweaker:ct_crusher_added"))
            .orElseThrow().value();
        var output = recipe.getResultItem(server.registryAccess());
        require(output.is(net.minecraft.world.item.Items.DIAMOND)
            && output.getCount() == 2, "recipe survives reload");
        require(manager
            .byKey(ResourceLocation.parse("crafttweaker:ct_crusher_removed"))
            .isEmpty(), "removal survives reload");
        long count = manager
            .getAllRecipesFor(
                zone.moddev.mc.lib.common.recipes.CrushingRecipe.TYPE.get())
            .stream().filter(holder -> holder.id().toString()
                .equals("crafttweaker:ct_crusher_added"))
            .count();
        require(count == 1, "recipe reload does not duplicate IDs");
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("mmdlib.craftTweakerSmoke") || finished
            || stage == 0)
            return;
        var mc = Minecraft.getInstance();
        try {
            if (++frames < 30)
                return;
            if (stage == 1) {
                Screenshot.grab(mc.gameDirectory, "ct-directional-world.png",
                    mc.getMainRenderTarget(), message -> {
                    });
                if (present()) {
                    mc.setScreen(new Gallery());
                    stage = 2;
                    frames = 0;
                    return;
                }
            } else
                Screenshot.grab(mc.gameDirectory, "ct-items-and-names.png",
                    mc.getMainRenderTarget(), message -> {
                    });
            MMDLib.LOGGER.info(
                "MMDLIB_CT_CLIENT_PASS: CraftTweaker installed={}, integrated world, resource checks and captures complete",
                present());
            finished = true;
            mc.stop();
        } catch (Throwable error) {
            fail(error);
        }
    }

    private static void fail(Throwable error) {
        finished = true;
        MMDLib.LOGGER.error("MMDLIB_CT_CLIENT_FAIL", error);
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
    }

    private static final class Gallery extends Screen {
        Gallery() {
            super(Component.literal("CraftTweaker content smoke"));
        }
        @Override
        public boolean isPauseScreen() {
            return false;
        }
        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY,
            float partialTick) {
            g.fill(0, 0, width, height, 0xFF26313C);
            g.drawString(font, title, 30, 25, 0xFFFFFFFF);
            int y = 65;
            for (String name : ITEMS) {
                var stack = new ItemStack(BuiltInRegistries.ITEM.get(id(name)));
                g.pose().pushPose();
                g.pose().translate(35, y, 0);
                g.pose().scale(2, 2, 2);
                g.renderItem(stack, 0, 0);
                g.pose().popPose();
                g.drawString(font, stack.getHoverName(), 90, y + 10,
                    0xFFFFFFFF);
                y += 62;
            }
        }
    }
}
