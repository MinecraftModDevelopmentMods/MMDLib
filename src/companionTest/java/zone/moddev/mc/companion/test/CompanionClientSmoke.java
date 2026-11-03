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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

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
  private static int stage, frames, waitingTicks, drawerStage, fluidStage;

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!Boolean.getBoolean("mmdlib.companionSmoke") || finished) return;
    var mc = Minecraft.getInstance();
    try {
      if (System.currentTimeMillis() > DEADLINE) throw new AssertionError("Timed out on " + mc.screen);
      if (!started && mc.screen instanceof TitleScreen) {
        started = true;
        var settings = new LevelSettings("Companion smoke", GameType.CREATIVE, false,
            Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("companion-" + System.currentTimeMillis(),
            settings, new WorldOptions(1, false, false),
            registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
      }
      if (mc.player == null || mc.level == null) return;
      if (opening) {
        if (++waitingTicks > 400)
          throw new AssertionError("Menu " + stage + " did not render; screen=" + mc.screen
              + ", block entity=" + mc.level.getBlockEntity(POS));
        var entity = mc.level.getBlockEntity(POS);
        if (!menuRequested && entity != null
            && entity.getType() == CompanionMod.MACHINES.get(stage).type.get()) {
          menuRequested = true;
          mc.getSingleplayerServer().execute(() -> {
            try {
              var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
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
        MMDLib.LOGGER.info("COMPANION_CLIENT_PASS: all four menus rendered and received their values");
        mc.stop();
        return;
      }
      opening = true;
      waitingTicks = 0;
      int current = stage;
      mc.getSingleplayerServer().execute(() -> {
        try {
          var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
          var level = player.serverLevel();
          player.closeContainer();
          level.getChunkAt(POS);
          level.setBlock(POS.offset(0, 0, 2),
              net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
          level.setBlock(POS, CompanionMod.MACHINES.get(current).block.get().defaultBlockState(), 3);
          var entity = level.getBlockEntity(POS);
          if (entity instanceof ProcessorEntity machine) {
            machine.energy.setStored(current == 0 ? 80_000 : current == 1 ? 40_000 : 10_000);
            if (current == 0) machine.fluids.fill(new FluidStack(Fluids.WATER, 2_500), FluidAction.EXECUTE);
            machine.items.insertItem(0, new ItemStack(current == 1 ? Items.IRON_INGOT : Items.COPPER_ORE,
                current == 1 ? 1 : 16), false);
            for (int i = 0; i < 40; i++) machine.serverTick();
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
      var energy = menu.bound.screen().find("energy").orElseThrow();
      if (!(energy instanceof zone.moddev.mc.lib.gui.model.Element.EnergyGauge))
        throw new AssertionError("Every companion screen must use the reusable battery gauge");
      if (screen.layout.bounds(energy).right() != screen.layout.panel().width() - 7)
        throw new AssertionError("Energy must sit against the right panel margin");
      if (!(menu.value(energy, false) instanceof Number)) throw new AssertionError("Energy was not synced");
      if (stage < 3) {
        var progress = menu.bound.screen().find("progress").orElseThrow();
        if (!(menu.value(progress, true) instanceof Number n) || n.intValue() <= 0)
          throw new AssertionError("Cycle time was not synced");
        if (menu.getSlot(0).getItem().isEmpty()) throw new AssertionError("Input was not synced");
      }
      String name = menu.bound.screen().id().getPath();
      if (stage == 0) {
        var tank = menu.bound.screen().find("tank").orElseThrow();
        var expectedFluid = fluidStage == 0 ? Fluids.WATER : Fluids.LAVA;
        int expectedAmount = fluidStage == 0 ? 2_500 : fluidStage == 3 ? 500 : fluidStage == 5 ? 0 : 1_500;
        if (!(menu.value(tank, false) instanceof FluidStack fluid)
            || (expectedAmount > 0 && fluid.getFluid() != expectedFluid) || fluid.getAmount() != expectedAmount) {
          if (fluidStage > 0) return; // Wait for fluid changes to reach the menu.
          throw new AssertionError("Water was not synced into the tank gauge");
        }
        if (!(menu.value(tank, true) instanceof Number capacity) || capacity.intValue() != 4_000)
          throw new AssertionError("Tank capacity was not synced");
        if (fluidStage == 1) {
          Screenshot.grab(mc.gameDirectory, "crusher-lava.png", mc.getMainRenderTarget(), message -> {});
          fluidStage = 2;
          frames = 0;
          mc.getSingleplayerServer().execute(() -> {
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
          Screenshot.grab(mc.gameDirectory, "crusher-container.png", mc.getMainRenderTarget(), message -> {});
          fluidStage = 5;
          frames = 0;
          mc.getSingleplayerServer().execute(() -> {
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
          if (!menu.getCarried().isEmpty() || ((Number) menu.value(energy, false)).longValue() != 0) return;
          Screenshot.grab(mc.gameDirectory, "crusher-empty.png", mc.getMainRenderTarget(), message -> {});
          frames = 0;
          stage++;
          opening = false;
          menuRequested = false;
          return;
        }
      }
      if (stage < 3) {
        var info = (zone.moddev.mc.lib.gui.model.Element.Popout)
            menu.bound.screen().find("info").orElseThrow();
        var tab = screen.bounds(info);
        if (!info.iconOrDefault().toString().equals("mmdlib:icon_info"))
          throw new AssertionError("The information drawer must use the info icon");
        if (drawerStage == 0) {
          Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> {});
          screen.mouseClicked(tab.x() + 10, tab.y() + 10, 0);
          var exclusions = screen.exclusions();
          if (exclusions.size() != 2 || exclusions.get(1).right() != tab.x())
            throw new AssertionError("The drawer must extend left while retaining its icon tab");
          if (exclusions.get(0).bottom() != exclusions.get(1).bottom()
              || exclusions.get(1).bottom() != screen.guiTop() + screen.layout.panel().height())
            throw new AssertionError("The drawer and its rail must reach the machine panel's bottom");
          drawerStage = 1;
          frames = 0;
          return;
        }
        Screenshot.grab(mc.gameDirectory, name + "-drawer.png", mc.getMainRenderTarget(), message -> {});
        if (!screen.mouseClicked(tab.x() + 10, screen.exclusions().getFirst().bottom() - 8, 0))
          throw new AssertionError("Clicks on the drawer rail must not pass through the screen");
        screen.mouseScrolled(tab.x() - 10, tab.y() + 10, 0, -1);
        screen.mouseClicked(tab.x() + 10, tab.y() + 10, 0);
        if (screen.exclusions().size() != 1)
          throw new AssertionError("The icon tab did not close its drawer");
        drawerStage = 0;
        if (stage == 0) {
          fluidStage = 1;
          frames = 0;
          mc.getSingleplayerServer().execute(() -> {
            var machine = (ProcessorEntity) mc.getSingleplayerServer().overworld().getBlockEntity(POS);
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
