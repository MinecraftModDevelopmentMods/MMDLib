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

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
  private static int stage, frames, waitingTicks;

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
            machine.energy.setStored(80_000);
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
      var energy = menu.elements.stream().filter(e -> e instanceof zone.moddev.mc.lib.gui.model.Element.Meter)
          .findFirst().orElseThrow();
      if (!(menu.value(energy, false) instanceof Number)) throw new AssertionError("Energy was not synced");
      if (stage < 3) {
        var progress = menu.bound.screen().find("progress").orElseThrow();
        if (!(menu.value(progress, true) instanceof Number n) || n.intValue() <= 0)
          throw new AssertionError("Cycle time was not synced");
        if (menu.getSlot(0).getItem().isEmpty()) throw new AssertionError("Input was not synced");
      }
      String name = menu.bound.screen().id().getPath();
      Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> {});
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
