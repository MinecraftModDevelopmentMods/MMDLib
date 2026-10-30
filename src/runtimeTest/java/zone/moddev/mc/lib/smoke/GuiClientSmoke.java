package zone.moddev.mc.lib.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.runtime.*;

/** Creates an isolated integrated world and renders the actual network-opened GUI under Xvfb. */
@EventBusSubscriber(modid = "mmdlib", value = Dist.CLIENT)
public final class GuiClientSmoke {
  private static boolean started, placed, opening, finished;
  private static int ticks, frames, stage;
  private static long deadline = System.currentTimeMillis() + 180000;
  private static final BlockPos POS = new BlockPos(0, 100, 0);

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!Boolean.getBoolean("mmdlib.guiSmoke") || finished) return;
    var mc = Minecraft.getInstance();
    try {
      if (System.currentTimeMillis() > deadline)
        throw new AssertionError("GUI smoke timed out on " + mc.screen);
      if (!started && mc.screen instanceof TitleScreen) {
        started = true;
        var settings =
            new LevelSettings(
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
                registries ->
                    registries
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
                    ServerPlayer player =
                        mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    var level = player.serverLevel();
                    level.getChunkAt(POS);
                    level.setBlock(
                        POS,
                        BuiltInRegistries.BLOCK.get(GuiRuntimeChecks.ID).defaultBlockState(),
                        3);
                    level.setBlock(
                        POS.offset(0, 0, 2),
                        net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
                        3);
                    player.teleportTo(POS.getX() + .5, POS.getY() + 1, POS.getZ() + 2.5);
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
                    ServerPlayer player =
                        mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    var entity = (MachineSmoke.Counter) player.serverLevel().getBlockEntity(POS);
                    GuiRuntimeChecks.check(player, entity);
                    var recipe =
                        player
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
                    var use =
                        new zone.moddev.mc.lib.api.MaterialEventContexts.BlockUse(
                            player.level(),
                            POS,
                            entity.getBlockState(),
                            net.minecraft.core.Direction.UP,
                            net.minecraft.world.InteractionHand.MAIN_HAND,
                            net.minecraft.world.item.ItemStack.EMPTY);
                    zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL
                        .events()
                        .dispatch(
                            net.minecraft.resources.ResourceLocation.parse("mmdlib:open_menu"),
                            zone.moddev.mc.lib.api.MaterialEventTypes.BLOCK_RIGHT_CLICK,
                            new zone.moddev.mc.lib.api.MaterialEventContexts.BlockClick(
                                player, use));
                    if (!use.consumed() || !(player.containerMenu instanceof MachineMenu))
                      throw new AssertionError("block-right-click did not open screen");
                  } catch (Throwable error) {
                    fail(error);
                  }
                });
      }
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
        if (net.neoforged.fml.ModList.get().isLoaded("jei")) GuiJeiSmoke.check(screen);
      }
    } catch (Throwable error) {
      fail(error);
    }
  }

  @SubscribeEvent
  public static void frame(RenderFrameEvent.Post event) {
    if (!Boolean.getBoolean("mmdlib.guiSmoke") || finished) return;
    var mc = Minecraft.getInstance();
    if (stage == 0 && mc.screen instanceof MachineScreen screen && ticks >= 55 && ++frames == 5) {
      if (net.neoforged.fml.ModList.get().isLoaded("jei")) {
        if (screen.getMenu().slots.get(0).getItem().isEmpty()
            || !screen
                .getMenu()
                .slots
                .get(2)
                .getItem()
                .is(net.minecraft.world.item.Items.GOLD_INGOT)) {
          fail(new AssertionError("JEI transfer or ghost filter did not sync from server"));
          return;
        }
        MMDLib.LOGGER.info(
            "MMDLIB_GUI_JEI_SERVER_PASS: recipe ingredient moved to input and ghost filter synced"
                + " from server");
      }
      GuiGaugeSmoke.check(screen);
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
      if (!screen.getMenu().slots.get(1).isActive() || screen.getMenu().slots.get(0).isActive()) {
        fail(new AssertionError("Rendered context visibility incorrect"));
        return;
      }
      var config = screen.getMenu().bound.screen().find("config").orElseThrow();
      var r = screen.bounds(config);
      screen.mouseClicked(r.x() + 32, r.y() + 52, 0);
      stage = 2;
    }
    if (stage == 2 && mc.screen instanceof MachineScreen screen && ticks >= 85) {
      var config = screen.getMenu().bound.screen().find("config").orElseThrow();
      int key = -1 - screen.getMenu().elements.indexOf(config) * 65536;
      if (!java.math.BigInteger.valueOf(3).equals(screen.getMenu().wireValue(key))) {
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
      finished = true;
      mc.stop();
    }
  }

  private static void fail(Throwable error) {
    MMDLib.LOGGER.error("MMDLIB_GUI_CLIENT_FAIL", error);
    finished = true;
    Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
  }
}
