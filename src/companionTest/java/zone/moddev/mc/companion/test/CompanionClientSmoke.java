package zone.moddev.mc.companion.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import zone.moddev.mc.companion.CompanionMod;
import zone.moddev.mc.companion.ProcessorEntity;
import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;

/** Opens all four networked menus in an isolated world and captures the rendered screens. */
@EventBusSubscriber(modid = CompanionMod.ID, value = Dist.CLIENT)
public final class CompanionClientSmoke {
  private static final BlockPos POS = new BlockPos(0, 100, 0);
  /**
   * Time the whole smoke may take once the title screen is up. Launch time is not counted: it varies
   * with the machine, and a slow CI runner with software rendering needs most of this budget for the
   * menus alone. A menu that never renders still fails within 400 ticks (see {@link #tick}).
   */
  private static final long BUDGET_MILLIS = 360_000;

  private static long deadline = Long.MAX_VALUE;
  private static boolean started, opening, menuRequested, finished;
  private static int stage, frames, waitingTicks, drawerStage, fluidStage, controlStage;
  private static boolean controlFixtureRequested;
  private static boolean redstoneSmokeDone;
  private static volatile boolean controlFixtureReady;

  @SubscribeEvent
  public static void render(ScreenEvent.Render.Pre event) {
    if (Boolean.getBoolean("mmdlib.companionSmoke")
        && !finished
        && opening
        && stage == 4
        && event.getScreen() instanceof MachineScreen screen
        && AutomationClientSmoke.renderTooltipCapture(
            screen, event.getGuiGraphics(), event.getPartialTick())) event.setCanceled(true);
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!Boolean.getBoolean("mmdlib.companionSmoke") || finished) return;
    var mc = Minecraft.getInstance();
    try {
      if (System.currentTimeMillis() > deadline)
        throw new AssertionError("Timed out on " + mc.screen);
      if (!started && mc.screen instanceof TitleScreen) {
        deadline = System.currentTimeMillis() + BUDGET_MILLIS;
        if (!net.neoforged.fml.ModList.get().isLoaded("jade"))
          throw new AssertionError("Jade must be installed in companion runs");
        MMDLib.LOGGER.info("JADE_COMPANION_PRESENT");
        started = true;
        var settings =
            new LevelSettings(
                "Companion smoke",
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows()
            .createFreshLevel(
                "companion-" + System.currentTimeMillis(),
                settings,
                new WorldOptions(1, false, false),
                registries ->
                    registries
                        .registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT)
                        .value()
                        .createWorldDimensions(),
                mc.screen);
      }
      if (mc.player == null || mc.level == null) return;
      if (opening) {
        if (++waitingTicks > 400)
          throw new AssertionError(
              "Menu "
                  + stage
                  + " did not render; screen="
                  + mc.screen
                  + ", block entity="
                  + mc.level.getBlockEntity(POS)
                  + ", control stage="
                  + controlStage
                  + ", fluid stage="
                  + fluidStage
                  + (mc.screen instanceof MachineScreen screen
                      ? ", cycle time="
                          + screen
                              .getMenu()
                              .bound
                              .screen()
                              .find("progress")
                              .map(element -> screen.getMenu().value(element, true))
                              .orElse("missing")
                      : ""));
        var entity = mc.level.getBlockEntity(POS);
        if (!menuRequested
            && entity != null
            && entity.getType() == CompanionMod.MACHINES.get(stage).type.get()) {
          menuRequested = true;
          mc.getSingleplayerServer()
              .execute(
                  () -> {
                    try {
                      var player =
                          mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                      if (!ScreenRuntime.open(player, player.serverLevel().getBlockEntity(POS)))
                        throw new AssertionError("Menu did not open");
                    } catch (Throwable error) {
                      fail(error);
                    }
                  });
        }
        return;
      }
      if (stage == CompanionMod.MACHINES.size()) {
        if (!DrawerMemoryClientSmoke.run()) return;
        finished = true;
        MMDLib.LOGGER.info(
            "COMPANION_CLIENT_PASS: all five menus rendered and received their values");
        mc.stop();
        return;
      }
      opening = true;
      waitingTicks = 0;
      int current = stage;
      mc.getSingleplayerServer()
          .execute(
              () -> {
                try {
                  var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                  var level = player.serverLevel();
                  player.closeContainer();
                  if (current == 0 && BackpackFixture.installed()) {
                    BackpackFixture.equip(player);
                    CuriosFixture.setup(player);
                  }
                  level.getChunkAt(POS);
                  level.setBlock(
                      POS.offset(0, 0, 2),
                      net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
                      3);
                  level.setBlock(
                      POS, CompanionMod.MACHINES.get(current).block.get().defaultBlockState(), 3);
                  if (current == 3) {
                    // Player stands south of both machines: the western neighbour is on the left.
                    level.setBlock(
                        POS,
                        CompanionMod.CREATIVE_POWER
                            .block
                            .get()
                            .defaultBlockState()
                            .setValue(
                                zone.moddev.mc.companion.DemoBlock.FACING,
                                net.minecraft.core.Direction.SOUTH),
                        3);
                    level.setBlock(
                        POS.west(),
                        CompanionMod.CRUSHER
                            .block
                            .get()
                            .defaultBlockState()
                            .setValue(
                                zone.moddev.mc.companion.DemoBlock.FACING,
                                net.minecraft.core.Direction.SOUTH),
                        3);
                  }
                  var entity = level.getBlockEntity(POS);
                  if (entity instanceof ProcessorEntity machine) {
                    machine.energy.setStored(
                        current == 0 ? 80_000 : current == 1 ? 40_000 : 10_000);
                    if (current == 0)
                      machine.fluids.fill(new FluidStack(Fluids.WATER, 2_500), FluidAction.EXECUTE);
                    machine.items.insertItem(
                        0,
                        new ItemStack(
                            current == 1 ? Items.IRON_INGOT : Items.COPPER_ORE,
                            current == 1 ? 1 : 16),
                        false);
                    for (int i = 0; i < 40; i++) machine.serverTick();
                    if (current == 4) {
                      level.setBlock(
                          POS.north(),
                          net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(),
                          3);
                      level.setBlock(
                          POS.west(),
                          net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState(),
                          3);
                      level.setBlock(
                          POS.east(),
                          net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(),
                          3);
                      level.setBlock(
                          POS.below(),
                          net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState(),
                          3);
                    }
                  }
                  player.teleportTo(POS.getX() + 0.5, POS.getY() + 1, POS.getZ() + 2.5);
                } catch (Throwable error) {
                  fail(error);
                }
              });
    } catch (Throwable error) {
      fail(error);
    }
  }

  @SubscribeEvent
  public static void frame(RenderFrameEvent.Post event) {
    if (!Boolean.getBoolean("mmdlib.companionSmoke") || finished || !opening) return;
    var mc = Minecraft.getInstance();
    if (!(mc.screen instanceof MachineScreen screen)) return;
    var menu = screen.getMenu();
    var expected = CompanionMod.MACHINES.get(stage).type.get();
    if (menu.blockEntity.getType() != expected || ++frames < 25) return;
    try {
      if (stage == 0 && (!CuriosClientSmoke.run(screen) || !BackpackClientSmoke.run(screen))) {
        frames = 0;
        waitingTicks = 0;
        return;
      }
      if (stage == 4 && !AutomationClientSmoke.run(screen)) {
        frames = 0;
        waitingTicks = 0;
        return;
      }
      if (stage == 0 && !CompanionWidgetSmoke.run(screen)) {
        frames = 0;
        waitingTicks = 0;
        return;
      }
      if (stage == 0 && !prepareControlFixture()) {
        frames = 0;
        waitingTicks = 0;
        return;
      }
      if (menu.automation != null && menu.bound.screen().playerInventory()) {
        var row = screen.layout.inventoryColors().orElseThrow();
        var inventory = screen.layout.inventory().orElseThrow();
        if (row.x() != inventory.x())
          throw new AssertionError("Colours align with inventory left edge");
        var firstStorage = zone.moddev.mc.lib.gui.layout.ScreenLayout.playerSlot(inventory, 9);
        var lastStorage = zone.moddev.mc.lib.gui.layout.ScreenLayout.playerSlot(inventory, 17);
        if (row.y() != inventory.y() || row.bottom() + 4 != firstStorage.y())
          throw new AssertionError("Toolbar shares the helmet row directly above inventory slots");
        for (String id : new String[] {"redstone", "toggle_0", "toggle_1"}) {
          var button = menu.bound.screen().find(id);
          if (button.isPresent()) {
            var rect = screen.layout.bounds(button.get());
            if (rect.y() != row.y()
                || rect.x() <= row.right()
                || rect.right() > lastStorage.right())
              throw new AssertionError(
                  "Colour selectors and machine controls share one non-overlapping row");
          }
        }
      }
      var energy = menu.bound.screen().find("energy").orElseThrow();
      if (!(energy instanceof zone.moddev.mc.lib.gui.model.Element.EnergyGauge))
        throw new AssertionError("Every companion screen must use the reusable battery gauge");
      if (screen.layout.bounds(energy).right() != screen.layout.panel().width() - 7)
        throw new AssertionError("Energy must sit against the right panel margin");
      if (!(menu.value(energy, false) instanceof Number))
        throw new AssertionError("Energy was not synced");
      if (stage == 0
          && !redstoneSmokeDone
          && !(redstoneSmokeDone = RedstoneClientSmoke.run(screen))) {
        frames = 0;
        waitingTicks = 0;
        return;
      }
      if (stage == 0 && !ProcessorButtonClientSmoke.run(screen)) {
        frames = 0;
        waitingTicks = 0;
        return;
      }
      if ((stage == 0 || stage == 2) && !StatsClientSmoke.run(screen)) {
        frames = 0;
        waitingTicks = 0;
        return;
      }
      if (stage == 3 && !CreativePowerClientSmoke.run(screen)) {
        frames = 0;
        waitingTicks = 0;
        return;
      }
      if (stage == 0 && controlStage < 3) {
        var dial = menu.bound.screen().find("progress").orElseThrow();
        var pause = menu.bound.screen().find("toggle_0").orElseThrow();
        var state =
            zone.moddev.mc.lib.gui.model.ProcessorButtonState.fromWire(menu.value(pause, false));
        if (state.isEmpty()) return;
        boolean paused =
            state.orElseThrow() == zone.moddev.mc.lib.gui.model.ProcessorButtonState.PAUSED;
        if (controlStage == 0 && !paused || controlStage == 1 && paused) {
          var bounds = screen.bounds(dial);
          screen.mouseClicked(bounds.x() + 10, bounds.y() + 10, 0);
          screen.mouseReleased(bounds.x() + 10, bounds.y() + 10, 0);
        } else if (controlStage != 2 || paused) return;
        controlStage++;
        frames = 0;
        return;
      }
      if (stage == 0 && !ReservationClientSmoke.run(screen)) {
        frames = 0;
        waitingTicks = 0;
        return;
      }
      // The empty-tank/empty-energy screenshot intentionally leaves the crusher idle.
      if (stage < 3 && !(stage == 0 && fluidStage == 5)) {
        var progress = menu.bound.screen().find("progress").orElseThrow();
        if (!(menu.value(progress, true) instanceof Number n) || n.intValue() <= 0)
          // Resume reserves the cycle on a later server tick. An idle snapshot between cycles
          // is also valid; render frames are not a barrier for server ticks or network updates.
          return;
        if (menu.getSlot(0).getItem().isEmpty()) throw new AssertionError("Input was not synced");
      }
      String name = menu.bound.screen().id().getPath();
      if (stage == 0) {
        var tank = menu.bound.screen().find("tank").orElseThrow();
        var expectedFluid = fluidStage == 0 ? Fluids.WATER : Fluids.LAVA;
        int expectedAmount =
            fluidStage == 0 ? 2_500 : fluidStage == 3 ? 500 : fluidStage == 5 ? 0 : 1_500;
        if (!(menu.value(tank, false) instanceof FluidStack fluid)
            || (expectedAmount > 0 && fluid.getFluid() != expectedFluid)
            || fluid.getAmount() != expectedAmount) {
          if (fluidStage > 0) return; // Wait for fluid changes to reach the menu.
          throw new AssertionError("Water was not synced into the tank gauge");
        }
        if (!(menu.value(tank, true) instanceof Number capacity) || capacity.intValue() != 4_000)
          throw new AssertionError("Tank capacity was not synced");
        if (fluidStage == 1) {
          Screenshot.grab(
              mc.gameDirectory, "crusher-lava.png", mc.getMainRenderTarget(), message -> {});
          fluidStage = 2;
          frames = 0;
          mc.getSingleplayerServer()
              .execute(
                  () -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    player.getAbilities().instabuild = false;
                    player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
                    player.containerMenu.broadcastChanges();
                  });
          return;
        }
        if (fluidStage == 2 || fluidStage == 3) {
          var expectedItem = fluidStage == 2 ? Items.BUCKET : Items.LAVA_BUCKET;
          if (!menu.getCarried().is(expectedItem)) return;
          var bounds = screen.bounds(tank);
          if (!screen.mouseClicked(bounds.x() + 9, bounds.y() + 24, 0))
            throw new AssertionError("The tank gauge did not handle the container click");
          fluidStage++;
          frames = 0;
          return;
        }
        if (fluidStage == 4) {
          if (!menu.getCarried().is(Items.BUCKET)) return;
          Screenshot.grab(
              mc.gameDirectory, "crusher-container.png", mc.getMainRenderTarget(), message -> {});
          fluidStage = 5;
          frames = 0;
          mc.getSingleplayerServer()
              .execute(
                  () -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    var machine = (ProcessorEntity) player.serverLevel().getBlockEntity(POS);
                    machine.fluids.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
                    machine.energy.setStored(0);
                    player.containerMenu.setCarried(ItemStack.EMPTY);
                    player.getAbilities().instabuild = true;
                    player.containerMenu.broadcastChanges();
                  });
          return;
        }
        if (fluidStage == 5) {
          if (!menu.getCarried().isEmpty() || ((Number) menu.value(energy, false)).longValue() != 0)
            return;
          Screenshot.grab(
              mc.gameDirectory, "crusher-empty.png", mc.getMainRenderTarget(), message -> {});
          frames = 0;
          stage++;
          opening = false;
          menuRequested = false;
          return;
        }
      }
      if (stage < 3) {
        var info =
            (zone.moddev.mc.lib.gui.model.Element.Popout)
                menu.bound.screen().find("info").orElseThrow();
        var tab = screen.bounds(info);
        if (!info.iconOrDefault().toString().equals("mmdlib:icon_info"))
          throw new AssertionError("The information drawer must use the info icon");
        if (drawerStage == 0) {
          Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> {});
          screen.mouseClicked(tab.x() + 10, tab.y() + 10, 0);
          var exclusions = screen.exclusions();
          if (exclusions.size()
                  != 4
                      + (menu.redstone == null ? 0 : 1)
                      + (menu.curiosDrawer.state().icons().isEmpty() ? 0 : 1)
              || exclusions.get(1).right() != tab.x())
            throw new AssertionError("The drawer must extend left while retaining its icon tab");
          if (exclusions.get(0).bottom() != exclusions.get(1).bottom()
              || exclusions.get(1).bottom() > screen.panelBounds().bottom() - 4
              || exclusions.get(1).bottom() > screen.drawerBottom())
            throw new AssertionError(
                "The info drawer must fit its content within the main panel and screen height"
                    + " limits");
          drawerStage = 1;
          frames = 0;
          return;
        }
        Screenshot.grab(
            mc.gameDirectory, name + "-drawer.png", mc.getMainRenderTarget(), message -> {});
        // The compact panel puts equipment tabs at the bottom of the same rail.
        // Exercise empty rail below the info tab, not another drawer's tab.
        int railY = tab.bottom() + 1; // Gap before the Statistics tab.
        if (!screen.mouseClicked(tab.x() + 10, railY, 0))
          throw new AssertionError("Clicks on the drawer rail must not pass through the screen");
        // Vanilla returns true even for unused buttons. Observe its recorded press to verify
        // these clicks reached inventory handling instead of being consumed by the drawer.
        var lastButton =
            net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class
                .getDeclaredField("lastClickButton");
        lastButton.setAccessible(true);
        for (int button : new int[] {2, 3, 4})
          for (var point :
              new double[][] {
                {tab.x() + 10, railY},
                {tab.x() - 10, tab.y() + 10}
              }) {
            lastButton.setInt(screen, -1);
            screen.mouseClicked(point[0], point[1], button);
            if (lastButton.getInt(screen) != button)
              throw new AssertionError("Drawer swallowed mouse button " + button);
            if (screen.exclusions().size()
                != 4
                    + (menu.redstone == null ? 0 : 1)
                    + (menu.curiosDrawer.state().icons().isEmpty() ? 0 : 1))
              throw new AssertionError("Extra mouse button changed the drawer state");
          }
        screen.mouseScrolled(tab.x() - 10, tab.y() + 10, 0, -1);
        screen.mouseClicked(tab.x() + 10, tab.y() + 10, 0);
        if (screen.exclusions().size()
            != 3
                + (menu.redstone == null ? 0 : 1)
                + (menu.curiosDrawer.state().icons().isEmpty() ? 0 : 1))
          throw new AssertionError("The icon tab did not close its drawer");
        drawerStage = 0;
        if (stage == 0) {
          fluidStage = 1;
          frames = 0;
          mc.getSingleplayerServer()
              .execute(
                  () -> {
                    var machine =
                        (ProcessorEntity)
                            mc.getSingleplayerServer().overworld().getBlockEntity(POS);
                    machine.fluids.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
                    machine.fluids.fill(new FluidStack(Fluids.LAVA, 1_500), FluidAction.EXECUTE);
                  });
          return;
        }
      } else {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> {});
      }
      frames = 0;
      stage++;
      opening = false;
      menuRequested = false;
    } catch (Throwable error) {
      fail(error);
    }
  }

  private static boolean prepareControlFixture() {
    if (controlFixtureReady) return true;
    if (!controlFixtureRequested) {
      controlFixtureRequested = true;
      var server = Minecraft.getInstance().getSingleplayerServer();
      server.execute(
          () -> {
            try {
              var machine = (ProcessorEntity) server.overworld().getBlockEntity(POS);
              // Reproduce a slow inventory/widget prelude without sleeping: its original recipe
              // inputs must be allowed to run out before we prepare the independent control phase.
              for (int i = 0; i < 2_000; i++) machine.serverTick();
              if (!machine.items.getStackInSlot(0).isEmpty())
                throw new AssertionError(
                    "Slow-prelude regression did not exhaust the original input");
              machine.lanes.forEach(zone.moddev.mc.lib.machine.feature.RecipeProcessor::reset);
              for (int i = 0; i < machine.items.getSlots(); i++)
                machine.items.setStackInSlot(i, ItemStack.EMPTY);
              machine.items.setStackInSlot(0, new ItemStack(Items.COPPER_ORE, 16));
              machine.energy.setStored(80_000);
              for (int i = 0; i < 40; i++) machine.serverTick();
              if (machine.lanes.getFirst().time() <= 0)
                throw new AssertionError("Fresh control fixture did not start processing");
              server.getPlayerList().getPlayers().getFirst().containerMenu.broadcastChanges();
              controlFixtureReady = true;
            } catch (Throwable error) {
              fail(error);
            }
          });
    }
    return false;
  }

  static void fail(Throwable error) {
    finished = true;
    MMDLib.LOGGER.error("COMPANION_CLIENT_FAIL", error);
    Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
  }
}
