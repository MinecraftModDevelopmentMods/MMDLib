package zone.moddev.mc.lib.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
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
import zone.moddev.mc.lib.gui.model.containers.PlayerInventoryElement;
import zone.moddev.mc.lib.gui.model.elements.SideConfigElement;
import zone.moddev.mc.lib.gui.runtime.ClientMenuItems;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor;

/** Creates an isolated integrated world and renders the actual network-opened GUI under Xvfb. */
@EventBusSubscriber(modid = "mmdlib", value = Dist.CLIENT)
public final class GuiClientSmoke {
    private static boolean started, placed, opening, finished;
    private static int ticks, frames, stage, syncStage, syncTicks, plansStage, plansTicks;
    private static net.minecraft.world.item.ItemStack savedInput = net.minecraft.world.item.ItemStack.EMPTY;
    private static long deadline = System.currentTimeMillis() + 180000;
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static final BlockPos CRUSHER = POS.offset(3, 0, 0);

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mmdlib.guiSmoke") || finished)
            return;
        var mc = Minecraft.getInstance();
        try {
            if (System.currentTimeMillis() > deadline)
                throw new AssertionError("GUI smoke timed out on " + mc.screen);
            if (!started && mc.screen instanceof TitleScreen) {
                if (Boolean.getBoolean("mmdlib.ghostSmokeOnly"))
                    GuiIngredientDropSmoke.install();
                started = true;
                var settings = new LevelSettings(
                    "GUI smoke",
                    GameType.CREATIVE,
                    false,
                    Difficulty.PEACEFUL,
                    true,
                    new GameRules(),
                    WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows()
                    .createFreshLevel(
                        "gui-smoke-" + System.currentTimeMillis(),
                        settings,
                        new WorldOptions(1, false, false),
                        registries -> registries
                            .registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT)
                            .value()
                            .createWorldDimensions(),
                        mc.screen);
            }
            if (mc.player != null && mc.level != null && !placed) {
                placed = true;
                mc.getSingleplayerServer()
                    .execute(
                        () -> {
                            try {
                                ServerPlayer player = mc.getSingleplayerServer().getPlayerList()
                                    .getPlayers().getFirst();
                                var level = player.serverLevel();
                                level.getChunkAt(POS);
                                level.setBlock(
                                    POS,
                                    BuiltInRegistries.BLOCK.get(GuiRuntimeChecks.ID)
                                        .defaultBlockState(),
                                    3);
                                level.setBlock(
                                    POS.offset(0, 0, 2),
                                    net.minecraft.world.level.block.Blocks.STONE
                                        .defaultBlockState(),
                                    3);
                                level.setBlock(
                                    CRUSHER,
                                    BuiltInRegistries.BLOCK.get(CrusherSmoke.ID)
                                        .defaultBlockState(),
                                    3);
                                player.teleportTo(POS.getX() + .5, POS.getY() + 1,
                                    POS.getZ() + 2.5);
                            } catch (Throwable error) {
                                fail(error);
                            }
                        });
            }
            if (placed
                && !opening
                && mc.player != null
                && mc.level != null
                && mc.level.getBlockEntity(POS) instanceof MachineSmoke.Counter) {
                opening = true;
                mc.getSingleplayerServer()
                    .execute(
                        () -> {
                            try {
                                ServerPlayer player = mc.getSingleplayerServer().getPlayerList()
                                    .getPlayers().getFirst();
                                var entity = (MachineSmoke.Counter) player.serverLevel()
                                    .getBlockEntity(POS);
                                GuiRuntimeChecks.check(player, entity);
                                var recipe = player
                                    .serverLevel()
                                    .getRecipeManager()
                                    .getAllRecipesFor(
                                        zone.moddev.mc.lib.common.recipes.CrushingRecipe.TYPE.get())
                                    .getFirst();
                                player
                                    .getInventory()
                                    .setItem(
                                        9,
                                        recipe
                                            .value()
                                            .getIngredients()
                                            .getFirst()
                                            .getItems()[0]
                                            .copyWithCount(8));
                                var use = new zone.moddev.mc.lib.api.MaterialEventContexts.BlockUse(
                                    player.level(),
                                    POS,
                                    entity.getBlockState(),
                                    net.minecraft.core.Direction.UP,
                                    net.minecraft.world.InteractionHand.MAIN_HAND,
                                    net.minecraft.world.item.ItemStack.EMPTY);
                                zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL
                                    .events()
                                    .dispatch(
                                        net.minecraft.resources.ResourceLocation
                                            .parse("mmdlib:open_menu"),
                                        zone.moddev.mc.lib.api.MaterialEventTypes.BLOCK_RIGHT_CLICK,
                                        new zone.moddev.mc.lib.api.MaterialEventContexts.BlockClick(
                                            player, use));
                                if (!use.consumed()
                                    || !(player.containerMenu instanceof MachineMenu))
                                    throw new AssertionError(
                                        "block-right-click did not open screen");
                            } catch (Throwable error) {
                                fail(error);
                            }
                        });
            }
            if (Boolean.getBoolean("mmdlib.transferSmokeOnly")) {
                if (mc.screen instanceof MachineScreen screen) {
                    if (++ticks == 30)
                        GuiJeiSmoke.transferOnly(screen);
                    if (ticks > 35 && !screen.getMenu().slots.get(0).getItem().isEmpty()) {
                        finished = true;
                        MMDLib.LOGGER.info(
                            "MMDLIB_GUI_TRANSFER_PASS: named-only destinations, offhand exclusion and server"
                                + " transfer");
                        mc.stop();
                    }
                    if (ticks > 90)
                        throw new AssertionError("Named recipe transfer did not synchronize");
                }
                return;
            }
            if (Boolean.getBoolean("mmdlib.ghostSmokeOnly")) {
                if (mc.screen instanceof MachineScreen screen
                    && GuiGhostSmoke.tick(screen)
                    && GuiIngredientDropSmoke.tick(screen)) {
                    finished = true;
                    MMDLib.LOGGER.info(
                        "MMDLIB_GUI_GHOST_PASS: typed JEI drops, pending edits, clicks, clearing and hidden"
                            + " targets; custom fluid-drop elements and rejected/closed-screen drops");
                    mc.stop();
                }
                return;
            }
            checkFeatureSync(mc);
            if (stage == 4)
                checkPlans(mc);
            if (mc.screen instanceof MachineScreen screen && ++ticks == 30) {
                var energy = screen.getMenu().bound.screen().find("energy").orElseThrow();
                if (!(screen.getMenu().value(energy, false) instanceof java.math.BigInteger number)
                    || number.toString().length() != 101)
                    throw new AssertionError("Large value not synced to rendered menu");
                var info = screen.getMenu().bound.screen().find("info").orElseThrow();
                var r = screen.bounds(info);
                screen.mouseClicked(r.x() + 5, r.y() + 5, 0);
                if (screen.exclusions().size() < 5)
                    throw new AssertionError("Open popout exclusion absent");
                if (net.neoforged.fml.ModList.get().isLoaded("jei"))
                    GuiJeiSmoke.check(screen);
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (Boolean.getBoolean("mmdlib.transferSmokeOnly"))
            return;
        if (Boolean.getBoolean("mmdlib.ghostSmokeOnly")) {
            GuiIngredientDropSmoke.rendered();
            return;
        }
        if (!Boolean.getBoolean("mmdlib.guiSmoke") || finished)
            return;
        var mc = Minecraft.getInstance();
        if (stage == 0 && mc.screen instanceof MachineScreen screen && ticks >= 55
            && ++frames == 5) {
            if (net.neoforged.fml.ModList.get().isLoaded("jei")) {
                if (screen.getMenu().slots.get(0).getItem().isEmpty()
                    || !screen
                        .getMenu().slots
                        .get(2)
                        .getItem()
                        .is(net.minecraft.world.item.Items.GOLD_INGOT)) {
                    fail(new AssertionError(
                        "JEI transfer or ghost filter did not sync from server"));
                    return;
                }
                MMDLib.LOGGER.info(
                    "MMDLIB_GUI_JEI_SERVER_PASS: recipe ingredient moved to input and ghost filter synced"
                        + " from server");
            }
            var input = screen.getMenu().slots.get(0);
            var ingot = new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.IRON_INGOT);
            if (input.getMaxStackSize(ingot) != 16
                || !input.mayPlace(ingot)
                || input.mayPlace(
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT))) {
                fail(new AssertionError("Client menu ignored the input slot's limit or filter"));
                return;
            }
            GuiGaugeSmoke.check(screen);
            var inventoryBounds = screen.layout.region(PlayerInventoryElement.INVENTORY)
                .orElseThrow();
            int equipment = 0;
            for (var slot : screen.getMenu().slots) {
                if (slot.container != mc.player.getInventory() || slot.getContainerSlot() < 36)
                    continue;
                var expected = PlayerInventoryElement.slot(inventoryBounds,
                    slot.getContainerSlot());
                var element = screen.getMenu().nativeSlots().entry(slot.index).element();
                if (slot.x != expected.x() + 1
                    || slot.y != expected.y() + 1
                    || element == null || element.icon().isEmpty()) {
                    fail(new AssertionError("Client equipment position or empty icon missing"));
                    return;
                }
                equipment++;
            }
            if (equipment != 5) {
                fail(new AssertionError("Client equipment slots missing"));
                return;
            }
            Screenshot.grab(
                mc.gameDirectory,
                "gui-smoke.png",
                mc.getMainRenderTarget(),
                message -> MMDLib.LOGGER.info("GUI screenshot: {}", message.getString()));
            MMDLib.LOGGER.info(
                "MMDLIB_GUI_CLIENT_PASS: real menu open, custom value packet, all widget types rendered,"
                    + " popout opened, exclusions expanded");
            var navigation = screen.getMenu().bound.screen().find("navigate").orElseThrow();
            var r = screen.bounds(navigation);
            screen.mouseClicked(r.x() + 3, r.y() + 3, 0);
            stage = 1;
        }
        if (stage == 1 && mc.screen instanceof MachineScreen screen && ticks >= 70) {
            if (!screen.getMenu().slots.get(1).isActive()
                || screen.getMenu().slots.get(0).isActive()) {
                fail(new AssertionError("Rendered context visibility incorrect"));
                return;
            }
            if (screen
                .getMenu().slots
                .get(1)
                .mayPlace(
                    new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.IRON_INGOT))) {
                fail(new AssertionError("Client menu predicted placing an item in an output slot"));
                return;
            }
            var config = screen.getMenu().bound.screen().find("config").orElseThrow();
            var r = screen.bounds(config);
            screen.mouseClicked(r.x() + 32, r.y() + 52, 0);
            stage = 2;
        }
        if (stage == 2 && mc.screen instanceof MachineScreen screen && ticks >= 85) {
            var config = screen.getMenu().bound.screen().find("config").orElseThrow();
            if (screen.getMenu().elementStates().get(config, SideConfigElement.MODES)
                .getFirst() != 3) {
                fail(new AssertionError("Side face click did not sync from server"));
                return;
            }
            Screenshot.grab(
                mc.gameDirectory,
                "gui-sides-smoke.png",
                mc.getMainRenderTarget(),
                message -> MMDLib.LOGGER.info("GUI sides screenshot: {}", message.getString()));
            MMDLib.LOGGER.info(
                "MMDLIB_GUI_SIDES_PASS: actual context navigation, hidden slots, side-face click and"
                    + " authoritative sync");
            // Back to the first view, where the input slot is shown, for the plans check.
            var navigation = screen.getMenu().bound.screen().find("navigate").orElseThrow();
            var back = screen.bounds(navigation);
            screen.mouseClicked(back.x() + 3, back.y() + 3, 0);
            stage = 4;
        }
        // The plans check runs from the tick handler; the JEI-less run ends here once it passes.
        if (stage == 4 && plansStage == 7) {
            if (!net.neoforged.fml.ModList.get().isLoaded("jei")) {
                finished = true;
                mc.stop();
                return;
            }
            try {
                GuiJeiSmoke.showMachineRecipes();
            } catch (Throwable error) {
                fail(error);
                return;
            }
            frames = 0;
            stage = 3;
        }
        // JEI's recipe screen replaces the machine screen, so wait by frames rather than ticks.
        if (stage == 3 && ++frames == 30) {
            if (syncStage != 3)
                throw new AssertionError("feature sync did not finish: " + syncStage);
            Screenshot.grab(
                mc.gameDirectory,
                "gui-jei-machine-recipes.png",
                mc.getMainRenderTarget(),
                message -> MMDLib.LOGGER.info("JEI machine recipes screenshot: {}",
                    message.getString()));
            finished = true;
            mc.stop();
        }
    }

    /**
     * Checks the crusher's own client copy, with no menu open on it: its processor reaches RUNNING
     * with the cycle's time and no plans, and PAUSED after an interrupt on the server.
     */
    private static void checkFeatureSync(Minecraft mc) {
        if (syncStage == 3 || mc.level == null || mc.player == null)
            return;
        if (!(mc.level.getBlockEntity(CRUSHER) instanceof CrusherSmoke.Crusher client))
            return;
        var server = mc.getSingleplayerServer();
        if (syncStage == 0) {
            syncStage = 1;
            server.execute(
                () -> {
                    try {
                        CrusherSmoke.fill(
                            (CrusherSmoke.Crusher) server.overworld().getBlockEntity(CRUSHER));
                    } catch (Throwable error) {
                        fail(error);
                    }
                });
        } else if (syncStage == 1 && client.processor.status() == RecipeProcessor.Status.RUNNING) {
            CrusherSmoke.requireClientCopy(client);
            syncStage = 2;
            syncTicks = 0;
            server.execute(
                () -> ((CrusherSmoke.Crusher) server.overworld().getBlockEntity(CRUSHER)).processor
                    .interrupt());
        } else if (syncStage == 2) {
            if (client.processor.status() == RecipeProcessor.Status.PAUSED) {
                syncStage = 3;
                MMDLib.LOGGER.info(
                    "MMDLIB_GUI_FEATURE_SYNC_PASS: a nearby client saw the crusher run and pause");
            } else if (++syncTicks > 30) {
                throw new AssertionError(
                    "the client never saw the pause: " + client.processor.status());
            }
        }
    }

    /**
     * With the machine screen back on its first view, reserves one iron of ten (and room for four
     * more) on the server. The client's slot must refuse gold, cap a stack where the plan says, and
     * predict real clicks the way the server answers them; releasing the plan lifts all of it.
     */
    private static void checkPlans(Minecraft mc) {
        if (plansStage == 7 || !(mc.player.containerMenu instanceof MachineMenu menu))
            return;
        var server = mc.getSingleplayerServer();
        var slot = menu.slots.get(0);
        var iron = new net.minecraft.world.item.ItemStack(
            net.minecraft.world.item.Items.IRON_INGOT);
        var gold = new net.minecraft.world.item.ItemStack(
            net.minecraft.world.item.Items.GOLD_INGOT);
        var handler = ((net.neoforged.neoforge.items.SlotItemHandler) slot).getItemHandler();
        var plan = new zone.moddev.mc.lib.machine.inventory.SlotPlan(1, iron.copyWithCount(4));
        if (plansStage == 0) {
            if (!slot.isActive())
                return;
            plansStage = 1;
            server.execute(
                () -> {
                    try {
                        var items = ((MachineSmoke.Counter) server.overworld()
                            .getBlockEntity(POS)).items;
                        savedInput = items.getStackInSlot(0).copy();
                        items.setStackInSlot(0, iron.copyWithCount(10));
                        if (!items.reserve(0, plan))
                            throw new AssertionError("the server refused the reservation");
                    } catch (Throwable error) {
                        fail(error);
                    }
                });
        } else if (plansStage == 1) {
            if (!(handler instanceof ClientMenuItems client))
                throw new AssertionError("the client menu's slots are not ClientMenuItems");
            if (!client.hasPlan(0) || slot.getItem().getCount() != 10) {
                if (++plansTicks > 60)
                    throw new AssertionError("the plan never reached the client");
                return;
            }
            // 10 iron, take 1, add 4: the slot ends at 13, which is all it may hold.
            if (slot.mayPlace(gold) || !slot.mayPlace(iron))
                throw new AssertionError("the client does not hold the slot to the iron it keeps");
            if (slot.getMaxStackSize(iron) != 13)
                throw new AssertionError("planned max stack " + slot.getMaxStackSize(iron));
            int free = client.extractItem(0, 64, true).getCount();
            if (free != slot.getItem().getCount() - 1)
                throw new AssertionError("the client predicts " + free + " free to take");
            // A real click over the connection: left click takes what is free.
            mc.gameMode.handleInventoryMouseClick(
                menu.containerId, 0, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
            requireSlot(menu, 1, 9, "the client's prediction of taking the free iron");
            plansStage = 2;
            plansTicks = 0;
        } else if (plansStage == 2) {
            // Long enough for the server's answer to arrive and overwrite a wrong prediction.
            if (++plansTicks < 20)
                return;
            requireSlot(menu, 1, 9, "the client after the server answered taking");
            requireServerSlot(server, 1, 9, "taking");
            // Put it back: merging onto the planned iron is allowed.
            mc.gameMode.handleInventoryMouseClick(
                menu.containerId, 0, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
            requireSlot(menu, 10, 0, "the client's prediction of putting the iron back");
            plansStage = 3;
            plansTicks = 0;
        } else if (plansStage == 3) {
            if (++plansTicks < 20)
                return;
            requireSlot(menu, 10, 0, "the client after the server answered putting back");
            requireServerSlot(server, 10, 0, "putting back");
            plansStage = 4;
            server.execute(
                () -> {
                    try {
                        ((MachineSmoke.Counter) server.overworld().getBlockEntity(POS)).items
                            .release(0, plan);
                    } catch (Throwable error) {
                        fail(error);
                    }
                });
        } else if (plansStage == 4) {
            var client = (ClientMenuItems) handler;
            if (client.hasPlan(0)) {
                if (++plansTicks > 80)
                    throw new AssertionError("the release never reached the client");
                return;
            }
            if (client.extractItem(0, 64, true).getCount() != 10
                || slot.getMaxStackSize(iron) != 16
                || !slot.mayPlace(gold))
                throw new AssertionError("a released slot is still held");
            plansStage = 7;
            server.execute(
                () -> ((MachineSmoke.Counter) server.overworld().getBlockEntity(POS)).items
                    .setStackInSlot(0, savedInput));
            MMDLib.LOGGER.info(
                "MMDLIB_GUI_PLANS_PASS: the client menu held a reserved slot, predicted real clicks on"
                    + " it as the server answered them, and let it go again");
        }
    }

    private static void requireSlot(MachineMenu menu, int slot, int carried, String what) {
        if (menu.slots.get(0).getItem().getCount() != slot
            || menu.getCarried().getCount() != carried)
            throw new AssertionError(
                what
                    + ": slot "
                    + menu.slots.get(0).getItem().getCount()
                    + ", carried "
                    + menu.getCarried().getCount());
    }

    private static void requireServerSlot(
        net.minecraft.server.MinecraftServer server, int slot, int carried, String what) {
        var state = server
            .submit(
                () -> {
                    var menu = server.getPlayerList().getPlayers().getFirst().containerMenu;
                    return new int[]{
                        menu.getSlot(0).getItem().getCount(), menu.getCarried().getCount()
                    };
                })
            .join();
        if (state[0] != slot || state[1] != carried)
            throw new AssertionError(
                "the server after " + what + ": slot " + state[0] + ", carried " + state[1]);
    }

    private static void fail(Throwable error) {
        MMDLib.LOGGER.error("MMDLIB_GUI_CLIENT_FAIL", error);
        finished = true;
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
    }
}
