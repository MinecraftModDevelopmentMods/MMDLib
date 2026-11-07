package zone.moddev.mc.companion.test;

import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;
import zone.moddev.mc.lib.machine.neoforge.*;

/** Focused real-client check of XML registration, generated layout, recipes and /reload. */
@EventBusSubscriber(modid = "mmdlib_companion", value = Dist.CLIENT)
public final class SimpleCrusherClientSmoke {
  private static final ResourceLocation ID =
      ResourceLocation.parse("mmdlib_companion:simple_crusher");
  private static final ResourceLocation TYPE =
      ResourceLocation.parse("mmdlib_companion:simple_crushing");
  private static final BlockPos POS = new BlockPos(0, 100, 0);
  private static final long DEADLINE = System.currentTimeMillis() + 240000;
  private static boolean started, placed, requested, finished, reloadRequested;
  private static volatile boolean reloaded;
  private static boolean recipesOpened;
  private static int frames;

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!Boolean.getBoolean("mmdlib.simpleCrusherSmoke") || finished) return;
    var mc = Minecraft.getInstance();
    try {
      if (System.currentTimeMillis() > DEADLINE)
        throw new AssertionError("Timed out: " + mc.screen);
      if (!started && mc.screen instanceof TitleScreen) {
        started = true;
        var settings =
            new LevelSettings(
                "SimpleCrusher smoke",
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows()
            .createFreshLevel(
                "simple-crusher-" + System.currentTimeMillis(),
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
                        net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
                        3);
                player.teleportTo(.5, 100, 3.5);
                level.setBlock(POS, BuiltInRegistries.BLOCK.get(ID).defaultBlockState(), 3);
                var machine = (DefinedMachineEntity) level.getBlockEntity(POS);
                machine.energy.setStored(90000);
                machine.items.insertItem(0, new ItemStack(Items.IRON_ORE, 32), false);
                for (int i = 0; i < 31; i++) machine.serverTick();
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
                if (!ScreenRuntime.open(player, player.serverLevel().getBlockEntity(POS)))
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
    if (!Boolean.getBoolean("mmdlib.simpleCrusherSmoke") || finished) return;
    var mc = Minecraft.getInstance();
    if (recipesOpened) {
      if (++frames < 10) return;
      finished = true;
      Screenshot.grab(
          mc.gameDirectory, "simple-crusher-recipes.png", mc.getMainRenderTarget(), message -> {});
      MMDLib.LOGGER.info(
          "SIMPLE_CRUSHER_CLIENT_PASS: centered screen, arrow recipe click, assets, synchronized"
              + " recipes and reload");
      mc.stop();
      return;
    }
    if (!(mc.screen instanceof MachineScreen screen)
        || !screen.getMenu().bound.screen().id().equals(ID)) return;
    try {
      if (++frames < 30) return;
      var menu = screen.getMenu();
      var progress = menu.bound.screen().find("generated_progress").orElseThrow();
      if (menu.value(progress, true) == null)
        throw new AssertionError("Progress maximum not synced");
      if (MachineContent.recipeType(TYPE).recipes(mc.level.getRecipeManager()).isEmpty())
        throw new AssertionError("Generated recipes not synced");
      for (var name : java.util.List.of("cobblestone", "gravel"))
        if (mc.level
            .getRecipeManager()
            .byKey(ResourceLocation.parse("mmdlib_companion:simple_crushing/stone/" + name))
            .isEmpty()) throw new AssertionError("Bundled recipe not synchronized: " + name);
      var model = mc.getBlockRenderer().getBlockModel(mc.level.getBlockState(POS));
      if (model.getParticleIcon().contents().name().getPath().equals("missingno"))
        throw new AssertionError("Generated block model missing");
      if (!reloadRequested) {
        reloadRequested = true;
        Screenshot.grab(
            mc.gameDirectory, "simple-crusher.png", mc.getMainRenderTarget(), message -> {});
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
                                  throw new AssertionError("Recipes missing after reload");
                                reloaded = true;
                                var player = server.getPlayerList().getPlayers().getFirst();
                                ScreenRuntime.open(
                                    player, player.serverLevel().getBlockEntity(POS));
                              } catch (Throwable failure) {
                                fail(failure);
                              }
                            });
                      });
            });
        frames = 0;
        return;
      }
      if (!reloaded) return;
      Screenshot.grab(
          mc.gameDirectory, "simple-crusher-reloaded.png", mc.getMainRenderTarget(), message -> {});
      var field =
          Class.forName("zone.moddev.mc.lib.client.jei.MachineJei").getDeclaredField("runtime");
      field.setAccessible(true);
      var runtime = (mezz.jei.api.runtime.IJeiRuntime) field.get(null);
      var type =
          zone.moddev.mc.lib.client.jei.MachineRecipeCategory.recipeType(
              MachineContent.recipeType(TYPE));
      if (runtime == null
          || runtime.getRecipeManager().createRecipeLookup(type).get().findAny().isEmpty())
        throw new AssertionError("JEI crusher recipes unavailable");
      if (runtime
          .getRecipeManager()
          .createRecipeLookup(type)
          .get()
          .noneMatch(
              holder ->
                  holder
                      .id()
                      .equals(
                          ResourceLocation.parse(
                              "mmdlib_companion:simple_crushing/stone/cobblestone"))))
        throw new AssertionError("Bundled recipe missing from JEI after reload");
      var longestExpectedDetails =
          net.minecraft.network.chat.Component.translatable("jei.mmdlib.machine.energy", "2,900")
              .append(" · ")
              .append(
                  net.minecraft.network.chat.Component.translatable(
                      "jei.mmdlib.machine.time", "7.25"));
      if (runtime.getRecipeManager().getRecipeCategory(type).getWidth()
          < mc.font.width(longestExpectedDetails))
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
    MMDLib.LOGGER.error("SIMPLE_CRUSHER_CLIENT_FAIL", error);
    Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
  }

  private static java.util.concurrent.CompletableFuture<Void> reloadAfterRejectedCandidate(
      net.minecraft.server.MinecraftServer server) {
    var selected = java.util.List.copyOf(server.getPackRepository().getSelectedIds());
    var active = server.getRecipeManager();
    var before = java.util.List.copyOf(active.getRecipes());
    var pack =
        server
            .getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR)
            .resolve("mmdlib-invalid-reload");
    try {
      var recipe = pack.resolve("data/mmdlib_companion/recipe/rejected.json");
      java.nio.file.Files.createDirectories(recipe.getParent());
      int format =
          net.minecraft.SharedConstants.getCurrentVersion()
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
                  throw new AssertionError("Invalid authored group did not reject reload");
                if (server.getRecipeManager() != active
                    || !java.util.List.copyOf(active.getRecipes()).equals(before))
                  throw new AssertionError("Rejected reload changed the active recipes");
                MMDLib.LOGGER.info("SIMPLE_CRUSHER_REJECTED_RELOAD_PASS: active recipes preserved");
                return server.reloadResources(selected);
              },
              server)
          .thenCompose(future -> future);
    } catch (java.io.IOException error) {
      return java.util.concurrent.CompletableFuture.failedFuture(error);
    }
  }
}
