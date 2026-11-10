package zone.moddev.mc.companion.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;

/** Focused real-client check of XML registration, generated layout, recipes and /reload. */
@EventBusSubscriber(modid = "mmdlib_companion", value = Dist.CLIENT)
public final class SimpleCrusherClientSmoke {
    private static final boolean LAVA = Boolean.getBoolean("mmdlib.simpleLavaFurnaceSmoke");
    private static final String NAME = LAVA ? "simple-lava-furnace" : "simple-crusher";
    private static final String MARKER = LAVA ? "SIMPLE_LAVA_FURNACE" : "SIMPLE_CRUSHER";
    private static final ResourceLocation ID = ResourceLocation.parse(
        "mmdlib_companion:" + (LAVA ? "simple_lava_furnace" : "simple_crusher"));
    private static final ResourceLocation TYPE = ResourceLocation
        .parse("mmdlib_companion:" + (LAVA ? "lava_smelting" : "simple_crushing"));
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static final long DEADLINE = System.currentTimeMillis() + 240000;
    private static boolean started, placed, requested, finished, reloadRequested;
    private static volatile boolean reloaded;
    private static boolean recipesOpened;
    private static int frames;
    private static int tooltipStage;
    private static int transferStage;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if ((!Boolean.getBoolean("mmdlib.simpleCrusherSmoke") && !LAVA) || finished)
            return;
        var mc = Minecraft.getInstance();
        try {
            if (System.currentTimeMillis() > DEADLINE)
                throw new AssertionError("Timed out: " + mc.screen);
            if (!started && mc.screen instanceof TitleScreen) {
                started = true;
                var settings = new LevelSettings(
                    NAME + " smoke",
                    GameType.CREATIVE,
                    false,
                    Difficulty.PEACEFUL,
                    true,
                    new GameRules(),
                    WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows()
                    .createFreshLevel(
                        NAME + "-" + System.currentTimeMillis(),
                        settings,
                        new WorldOptions(1, false, false),
                        registries -> registries
                            .registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT)
                            .value()
                            .createWorldDimensions(),
                        mc.screen);
            }
            if (mc.player == null || mc.level == null)
                return;
            var server = mc.getSingleplayerServer();
            if (!placed) {
                placed = true;
                server.execute(
                    () -> {
                        try {
                            var player = server.getPlayerList().getPlayers().getFirst();
                            var level = player.serverLevel();
                            level.getChunkAt(POS);
                            for (int x = -5; x <= 5; x++)
                                for (int z = -5; z <= 5; z++)
                                    level.setBlock(
                                        new BlockPos(x, 99, z),
                                        net.minecraft.world.level.block.Blocks.STONE
                                            .defaultBlockState(),
                                        3);
                            player.teleportTo(.5, 100, 3.5);
                            level.setBlock(POS, BuiltInRegistries.BLOCK.get(ID).defaultBlockState(),
                                3);
                            var machine = (DefinedMachineEntity) level.getBlockEntity(POS);
                            if (LAVA)
                                machine.fluids.fill(
                                    new net.neoforged.neoforge.fluids.FluidStack(
                                        net.minecraft.world.level.material.Fluids.LAVA, 4000),
                                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                            else
                                machine.energy.setStored(90000);
                            machine.items.insertItem(0, new ItemStack(Items.IRON_ORE, 32), false);
                            for (int i = 0; i < 31; i++)
                                machine.serverTick();
                        } catch (Throwable error) {
                            fail(error);
                        }
                    });
            }
            if (!requested && mc.level.getBlockEntity(POS) instanceof DefinedMachineEntity) {
                requested = true;
                server.execute(
                    () -> {
                        try {
                            var player = server.getPlayerList().getPlayers().getFirst();
                            if (!ScreenRuntime.open(player,
                                player.serverLevel().getBlockEntity(POS)))
                                throw new AssertionError("Menu refused");
                        } catch (Throwable error) {
                            fail(error);
                        }
                    });
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    @SubscribeEvent
    public static void rendered(RenderFrameEvent.Post event) {
        if ((!Boolean.getBoolean("mmdlib.simpleCrusherSmoke") && !LAVA) || finished)
            return;
        var mc = Minecraft.getInstance();
        if (recipesOpened) {
            if (++frames < 10)
                return;
            finished = true;
            Screenshot.grab(
                mc.gameDirectory, NAME + "-recipes.png", mc.getMainRenderTarget(), message -> {
                });
            MMDLib.LOGGER.info(
                MARKER
                    + "_CLIENT_PASS: centered screen, arrow recipe click, assets, synchronized"
                    + " recipes and reload");
            mc.stop();
            return;
        }
        if (!(mc.screen instanceof MachineScreen screen)
            || !screen.getMenu().bound.screen().id().equals(ID))
            return;
        try {
            if (++frames < 30)
                return;
            var menu = screen.getMenu();
            var inventoryViews = menu.bound.screen().elements().stream()
                .filter(zone.moddev.mc.lib.gui.model.containers.InventoryElement.class::isInstance)
                .map(zone.moddev.mc.lib.gui.model.containers.InventoryElement.class::cast)
                .toList();
            if (inventoryViews.size() < 2)
                throw new AssertionError("Generated independent inventory elements missing");
            for (var view : inventoryViews) {
                var outline = menu.elements.stream()
                    .filter(
                        zone.moddev.mc.lib.gui.model.containers.ColouredGroupElement.class::isInstance)
                    .map(zone.moddev.mc.lib.gui.model.containers.ColouredGroupElement.class::cast)
                    .filter(group -> group.child() == view)
                    .findFirst()
                    .orElseThrow();
                if (!menu.layout().bounds(outline).equals(menu.layout().bounds(view)))
                    throw new AssertionError("Group decoration changed inventory geometry");
                if (menu.components().get(BuiltinMenuComponents.AUTOMATION).inventory.groups()
                    .stream()
                    .filter(group -> group.inventory().equals(view.inventory()))
                    .noneMatch(
                        group -> group
                            .highlights()
                            .equals(java.util.List.of(outline.common().id().orElseThrow()))))
                    throw new AssertionError(
                        "Automation does not highlight the inventory's outline");
            }
            var seenSlots = new java.util.HashSet<Integer>();
            for (var view : inventoryViews)
                for (var slot : view.slots()) {
                    if (!seenSlots.add(menu.slotIndex(slot)))
                        throw new AssertionError("Local slots alias one menu index");
                    var bounds = menu.layout().bounds(slot);
                    int[] expected = slot.itemPosition(bounds);
                    if (menu.slot(slot).x != expected[0] || menu.slot(slot).y != expected[1])
                        throw new AssertionError(
                            "Inventory element did not position its native slot");
                }
            if (menu.playerSlotElements().stream().filter(s -> s.icon().isPresent()).count() != 5)
                throw new AssertionError("Shared slot appearance lost equipment placeholders");
            var progress = menu.bound.screen().find("generated_progress").orElseThrow();
            if (menu.value(progress, true) == null)
                throw new AssertionError("Progress maximum not synced");
            if (MachineContent.recipeType(TYPE).recipes(mc.level.getRecipeManager()).isEmpty())
                throw new AssertionError("Generated recipes not synced");
            if (!LAVA) {
                var energy = menu.bound.screen().find("energy").orElseThrow();
                if (!(energy instanceof zone.moddev.mc.lib.gui.model.elements.EnergyGaugeElement)
                    || !(menu.value(energy, false) instanceof java.math.BigInteger stored)
                    || stored.signum() <= 0
                    || !java.math.BigInteger.valueOf(100_000).equals(menu.value(energy, true)))
                    throw new AssertionError("Energy component did not synchronize");
                var tooltip = screen.tooltipLines(energy, 0, 0);
                String expected = zone.moddev.mc.lib.gui.client.ScreenText
                    .number(menu.value(energy, false), false)
                    + " / 100 k FE";
                if (tooltip.size() != 1 || !tooltip.getFirst().getString().equals(expected))
                    throw new AssertionError("Energy component tooltip changed: " + tooltip);
            }
            if (LAVA) {
                var gauge = menu.bound.screen().find("tank_0").orElseThrow();
                var gaugeBounds = screen.bounds(gauge);
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(
                    mc.getWindow().getWindow(),
                    (gaugeBounds.x() + gaugeBounds.width() / 2.)
                        * mc.getWindow().getScreenWidth()
                        / mc.getWindow().getGuiScaledWidth(),
                    (gaugeBounds.y() + gaugeBounds.height() / 2.)
                        * mc.getWindow().getScreenHeight()
                        / mc.getWindow().getGuiScaledHeight());
                if (!(menu.value(gauge,
                    false) instanceof net.neoforged.neoforge.fluids.FluidStack fluid)
                    || fluid.getAmount() <= 0
                    || fluid.getFluid() != net.minecraft.world.level.material.Fluids.LAVA)
                    throw new AssertionError("Lava gauge did not synchronize");
                if (menu.bound.screen().find("energy").isPresent())
                    throw new AssertionError("Unexpected FE gauge");
                var tooltip = screen.tooltipLines(gauge, 0, 0);
                if (tooltip.size() < 3
                    || !tooltip.get(0).getString().equals("fuel")
                    || !tooltip.get(1).getString().contains("mB of Lava")
                    || !tooltip.get(2).getString().equals("Accepts: Lava"))
                    throw new AssertionError("Tank tooltip did not synchronize: " + tooltip);
                if (tooltipStage < 3) {
                    if (tooltipStage > 0) {
                        String expected = tooltipStage == 1
                            ? "Accepts Lava in Bucket"
                            : "Doesn't accept Water in Bucket";
                        var color = tooltipStage == 1
                            ? net.minecraft.ChatFormatting.BLUE
                            : net.minecraft.ChatFormatting.RED;
                        if (!tooltip.getLast().getString().equals(expected)
                            || tooltip.getLast().getStyle().getColor().getValue() != color
                                .getColor())
                            throw new AssertionError("Wrong carried-container tooltip: " + tooltip);
                        Screenshot.grab(
                            mc.gameDirectory,
                            NAME + "-tooltip-" + tooltipStage + ".png",
                            mc.getMainRenderTarget(),
                            message -> {
                            });
                    }
                    final ItemStack next = tooltipStage == 0
                        ? new ItemStack(Items.LAVA_BUCKET)
                        : tooltipStage == 1 ? new ItemStack(Items.WATER_BUCKET) : ItemStack.EMPTY;
                    mc.getSingleplayerServer()
                        .execute(
                            () -> mc.getSingleplayerServer()
                                .getPlayerList()
                                .getPlayers()
                                .getFirst().containerMenu
                                .setCarried(next));
                    tooltipStage++;
                    frames = 0;
                    return;
                }
                if (transferStage == 0) {
                    transferStage = 1;
                    frames = 0;
                    mc.getSingleplayerServer()
                        .execute(
                            () -> {
                                var player = mc.getSingleplayerServer().getPlayerList().getPlayers()
                                    .getFirst();
                                var machine = (DefinedMachineEntity) player.serverLevel()
                                    .getBlockEntity(POS);
                                var features = machine.screenFeatures();
                                features.setProcessorPaused(
                                    features.processor(java.util.Optional.empty()).orElseThrow()
                                        .key(),
                                    true);
                                machine.fluids.restoreFluid(
                                    0,
                                    new net.neoforged.neoforge.fluids.FluidStack(
                                        net.minecraft.world.level.material.Fluids.LAVA, 4000));
                                player.getAbilities().instabuild = false;
                                player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
                                player.containerMenu.broadcastChanges();
                            });
                    return;
                }
                if (transferStage == 1 || transferStage == 2) {
                    var expected = transferStage == 1 ? Items.BUCKET : Items.LAVA_BUCKET;
                    int amount = transferStage == 1 ? 4000 : 3000;
                    if (!menu.getCarried().is(expected) || fluid.getAmount() != amount)
                        return;
                    if (!screen.mouseClicked(
                        gaugeBounds.x() + 9, gaugeBounds.y() + 24, transferStage == 1 ? 1 : 0))
                        throw new AssertionError("Generated gauge did not handle container click");
                    transferStage++;
                    frames = 0;
                    return;
                }
                if (transferStage == 3) {
                    if (!menu.getCarried().is(Items.BUCKET) || fluid.getAmount() != 4000)
                        return;
                    transferStage = 4;
                    Screenshot.grab(
                        mc.gameDirectory, NAME + "-container.png", mc.getMainRenderTarget(),
                        message -> {
                        });
                    MMDLib.LOGGER.info(
                        "SIMPLE_LAVA_FURNACE_CONTAINER_PASS: right drain, left fill, cursor and tank"
                            + " synchronized");
                }
            }
            if (!LAVA)
                for (var name : java.util.List.of("cobblestone", "gravel"))
                    if (mc.level
                        .getRecipeManager()
                        .byKey(ResourceLocation
                            .parse("mmdlib_companion:simple_crushing/stone/" + name))
                        .isEmpty())
                        throw new AssertionError("Bundled recipe not synchronized: " + name);
            var model = mc.getBlockRenderer().getBlockModel(mc.level.getBlockState(POS));
            if (model.getParticleIcon().contents().name().getPath().equals("missingno"))
                throw new AssertionError("Generated block model missing");
            if (!reloadRequested) {
                reloadRequested = true;
                Screenshot.grab(mc.gameDirectory, NAME + ".png", mc.getMainRenderTarget(),
                    message -> {
                    });
                var server = mc.getSingleplayerServer();
                server.execute(
                    () -> {
                        server.getPlayerList().getPlayers().getFirst().closeContainer();
                        reloadAfterRejectedCandidate(server)
                            .whenComplete(
                                (ignored, error) -> {
                                    if (error != null) {
                                        fail(error);
                                        return;
                                    }
                                    server.execute(
                                        () -> {
                                            try {
                                                if (MachineContent.recipeType(TYPE)
                                                    .recipes(server.getRecipeManager())
                                                    .isEmpty())
                                                    throw new AssertionError(
                                                        "Recipes missing after reload");
                                                reloaded = true;
                                                var player = server.getPlayerList().getPlayers()
                                                    .getFirst();
                                                ScreenRuntime.open(
                                                    player,
                                                    player.serverLevel().getBlockEntity(POS));
                                            } catch (Throwable failure) {
                                                fail(failure);
                                            }
                                        });
                                });
                    });
                frames = 0;
                return;
            }
            if (!reloaded)
                return;
            Screenshot.grab(
                mc.gameDirectory, NAME + "-reloaded.png", mc.getMainRenderTarget(), message -> {
                });
            var field = Class.forName("zone.moddev.mc.lib.client.jei.MachineJei")
                .getDeclaredField("runtime");
            field.setAccessible(true);
            var runtime = (mezz.jei.api.runtime.IJeiRuntime) field.get(null);
            var type = zone.moddev.mc.lib.client.jei.MachineRecipeCategory.recipeType(
                MachineContent.recipeType(TYPE));
            if (runtime == null
                || runtime.getRecipeManager().createRecipeLookup(type).get().findAny().isEmpty())
                throw new AssertionError("JEI crusher recipes unavailable");
            if (!LAVA
                && runtime
                    .getRecipeManager()
                    .createRecipeLookup(type)
                    .get()
                    .noneMatch(
                        holder -> holder
                            .id()
                            .equals(
                                ResourceLocation.parse(
                                    "mmdlib_companion:simple_crushing/stone/cobblestone"))))
                throw new AssertionError("Bundled recipe missing from JEI after reload");
            var longestExpectedDetails = net.minecraft.network.chat.Component
                .translatable("jei.mmdlib.machine.energy", "2,900")
                .append(" · ")
                .append(
                    net.minecraft.network.chat.Component.translatable(
                        "jei.mmdlib.machine.time", "7.25"));
            if (!LAVA
                && runtime.getRecipeManager().getRecipeCategory(type).getWidth() < mc.font
                    .width(longestExpectedDetails))
                throw new AssertionError("JEI category clips fractional processing time");
            var bounds = screen.bounds(progress);
            if (!screen.mouseClicked(
                bounds.x() + bounds.width() / 2., bounds.y() + bounds.height() / 2., 0)
                || mc.screen != runtime.getRecipesGui())
                throw new AssertionError("Processor arrow did not open JEI recipes");
            recipesOpened = true;
            frames = 0;
        } catch (Throwable error) {
            fail(error);
        }
    }

    private static void fail(Throwable error) {
        finished = true;
        MMDLib.LOGGER.error(MARKER + "_CLIENT_FAIL", error);
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
    }

    private static java.util.concurrent.CompletableFuture<Void> reloadAfterRejectedCandidate(
        net.minecraft.server.MinecraftServer server) {
        var selected = java.util.List.copyOf(server.getPackRepository().getSelectedIds());
        var active = server.getRecipeManager();
        var before = java.util.List.copyOf(active.getRecipes());
        var pack = server
            .getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR)
            .resolve("mmdlib-invalid-reload");
        try {
            var recipe = pack.resolve("data/mmdlib_companion/recipe/rejected.json");
            java.nio.file.Files.createDirectories(recipe.getParent());
            int format = net.minecraft.SharedConstants.getCurrentVersion()
                .getPackVersion(net.minecraft.server.packs.PackType.SERVER_DATA);
            java.nio.file.Files.writeString(
                pack.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":"
                    + format
                    + ",\"description\":\"Failed reload regression fixture\"}}");
            java.nio.file.Files.writeString(
                recipe,
                """
                    {"type":"mmdlib_companion:simple_crushing",
                     "inputs":{"misspelled_input":[{"items":{"item":"minecraft:stone"}}]},"time":1}
                    """);
            server.getPackRepository().reload();
            var failing = new java.util.ArrayList<>(selected);
            failing.add("file/mmdlib-invalid-reload");
            return server
                .reloadResources(failing)
                .handleAsync(
                    (ignored, error) -> {
                        if (error == null)
                            throw new AssertionError(
                                "Invalid authored group did not reject reload");
                        if (server.getRecipeManager() != active
                            || !java.util.List.copyOf(active.getRecipes()).equals(before))
                            throw new AssertionError("Rejected reload changed the active recipes");
                        MMDLib.LOGGER
                            .info("SIMPLE_CRUSHER_REJECTED_RELOAD_PASS: active recipes preserved");
                        return server.reloadResources(selected);
                    },
                    server)
                .thenCompose(future -> future);
        } catch (java.io.IOException error) {
            return java.util.concurrent.CompletableFuture.failedFuture(error);
        }
    }
}
