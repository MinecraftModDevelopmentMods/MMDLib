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
  private static final long DEADLINE = System.currentTimeMillis() + 180_000;
  private static boolean started, opening, menuRequested, finished;
  private static int stage, frames, waitingTicks, drawerStage, fluidStage, controlStage;

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!Boolean.getBoolean("mmdlib.companionSmoke") || finished) return;
    var mc = Minecraft.getInstance();
    try {
      if (System.currentTimeMillis() > DEADLINE)
        throw new AssertionError("Timed out on " + mc.screen);
      if (!started && mc.screen instanceof TitleScreen) {
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
                  level.getChunkAt(POS);
                  level.setBlock(
                      POS.offset(0, 0, 2),
                      net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
                      3);
                  level.setBlock(
                      POS, CompanionMod.MACHINES.get(current).block.get().defaultBlockState(), 3);
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
      var energy = menu.bound.screen().find("energy").orElseThrow();
      if (!(energy instanceof zone.moddev.mc.lib.gui.model.Element.EnergyGauge))
        throw new AssertionError("Every companion screen must use the reusable battery gauge");
      if (screen.layout.bounds(energy).right() != screen.layout.panel().width() - 7)
        throw new AssertionError("Energy must sit against the right panel margin");
      if (!(menu.value(energy, false) instanceof Number))
        throw new AssertionError("Energy was not synced");
      if (stage == 0 && controlStage < 5) {
        var dial = menu.bound.screen().find("progress").orElseThrow();
        if (!(dial instanceof zone.moddev.mc.lib.gui.model.Element.Custom)
            || !(menu.value(dial, false) instanceof Number)
            || !(menu.value(dial, true) instanceof Number))
          throw new AssertionError("The mod-defined dial and its values must reach the client");
        var pause = menu.bound.screen().find("toggle_0").orElseThrow();
        var redstone = menu.bound.screen().find("redstone").orElseThrow();
        if (!(menu.value(pause, false) instanceof Boolean paused)
            || !(menu.value(redstone, false) instanceof Number mode)) return;
        if (controlStage == 0 && !paused && mode.intValue() == 0) {
          var bounds = screen.bounds(dial);
          screen.mouseClicked(bounds.x() + 10, bounds.y() + 10, 0);
        } else if (controlStage == 1 && paused) {
          Screenshot.grab(
              mc.gameDirectory, "crusher-paused.png", mc.getMainRenderTarget(), message -> {});
          var bounds = screen.bounds(redstone);
          screen.mouseClicked(bounds.x() + 10, bounds.y() + 10, 0);
        } else if (controlStage == 2 && mode.intValue() == 1) {
          var bounds = screen.bounds(dial);
          screen.mouseClicked(bounds.x() + 10, bounds.y() + 10, 0);
        } else if (controlStage == 3 && !paused && mode.intValue() == 1) {
          Screenshot.grab(
              mc.gameDirectory, "crusher-redstone.png", mc.getMainRenderTarget(), message -> {});
          var bounds = screen.bounds(redstone);
          screen.mouseClicked(bounds.x() + 10, bounds.y() + 10, 0);
          screen.mouseClicked(bounds.x() + 10, bounds.y() + 10, 0);
        } else if (controlStage == 4 && !paused && mode.intValue() == 0) {
          // Wait for the server to acknowledge the redstone reset before checking the cycle.
        } else return;
        controlStage++;
        frames = 0;
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
          if (exclusions.size() != 3 || exclusions.get(1).right() != tab.x())
            throw new AssertionError("The drawer must extend left while retaining its icon tab");
          if (exclusions.get(0).bottom() != exclusions.get(1).bottom()
              || exclusions.get(1).bottom() > screen.guiTop() + screen.layout.panel().height()
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
        if (!screen.mouseClicked(tab.x() + 10, screen.exclusions().getFirst().bottom() - 8, 0))
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
                {tab.x() + 10, screen.exclusions().getFirst().bottom() - 8},
                {tab.x() - 10, tab.y() + 10}
              }) {
            lastButton.setInt(screen, -1);
            screen.mouseClicked(point[0], point[1], button);
            if (lastButton.getInt(screen) != button)
              throw new AssertionError("Drawer swallowed mouse button " + button);
            if (screen.exclusions().size() != 3)
              throw new AssertionError("Extra mouse button changed the drawer state");
          }
        screen.mouseScrolled(tab.x() - 10, tab.y() + 10, 0, -1);
        screen.mouseClicked(tab.x() + 10, tab.y() + 10, 0);
        if (screen.exclusions().size() != 2)
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

  private static void fail(Throwable error) {
    finished = true;
    MMDLib.LOGGER.error("COMPANION_CLIENT_FAIL", error);
    Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
  }
}
