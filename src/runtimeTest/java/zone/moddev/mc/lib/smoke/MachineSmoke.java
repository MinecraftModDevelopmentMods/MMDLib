package zone.moddev.mc.lib.smoke;

import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonParser;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.factories.BlockEntityFactory;
import zone.moddev.mc.lib.common.modules.DataModules;
import zone.moddev.mc.lib.common.runtime.MaterialEntityBlock;
import zone.moddev.mc.lib.common.runtime.RuntimeContentRegistrar;
import zone.moddev.mc.lib.common.types.*;
import zone.moddev.mc.lib.util.Registries;

/**
 * Registers a standalone {@code mmdlib:machine} with a ticking block entity, and a standalone item,
 * through a second runtime registrar, then checks them on a running server.
 */
@EventBusSubscriber(modid = "mmdlib", bus = EventBusSubscriber.Bus.MOD)
public final class MachineSmoke {
  private static final String NAMESPACE = "mmdlib_smoke";
  private static final ResourceLocation MACHINE = id("counter");
  private static final ResourceLocation ITEM = id("plain_nugget");
  private static final AtomicInteger TICKS = new AtomicInteger();
  private static volatile RuntimeContentRegistrar registrar;

  private MachineSmoke() {}

  /** The smoke registrar, which the general placement checks skip. */
  static RuntimeContentRegistrar registrar() {
    return registrar;
  }

  @SubscribeEvent
  static void construct(FMLConstructModEvent event) {
    Registries.BLOCK_ENTITY_FACTORIES.register(id("counter_entity"), new CounterFactory());
    var bus = ModList.get().getModContainerById("mmdlib").orElseThrow().getEventBus();
    registrar = new RuntimeContentRegistrar(NAMESPACE, MaterialExtensions.GLOBAL, bus);
    var types =
        net.neoforged.neoforge.registries.DeferredRegister.<BlockEntityType<?>>create(
            net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, NAMESPACE);
    types.register(bus);
    event.enqueueWork(
        () -> {
          var module =
              DataModules.Block.CODEC
                  .parse(
                      JsonOps.INSTANCE,
                      JsonParser.parseString(
                          """
                          {"factory":"mmdlib:machine","template":{},
                           "part":{"block_entity":{"factory":"mmdlib_smoke:counter_entity",
                             "factory_data":{"step":1}}}}
                          """))
                  .getOrThrow();
          var machine =
              new StandaloneBlock(
                  id("counter_module"),
                  FactoryData.EMPTY,
                  java.util.Optional.empty(),
                  MaterialRuntimeSettings.DEFAULT,
                  new FactoryData(JsonParser.parseString("{\"step\":3}")));
          registrar.queue(machine.resolve(MACHINE, module));
          registrar.blockEntityHandles().values().forEach(handle -> handle.register(types));
          if (Boolean.getBoolean("mmdlib.guiSmoke")) GuiRuntimeChecks.install();
          var item =
              new StandaloneItem(
                  ResourceLocation.parse("mmdlib:nugget"),
                  FactoryData.EMPTY,
                  java.util.Optional.empty(),
                  MaterialRuntimeSettings.DEFAULT);
          registrar.queue(
              item.resolve(
                  ITEM,
                  MaterialExtensions.GLOBAL.itemModule(ResourceLocation.parse("mmdlib:nugget"))));
        });
  }

  /** Places the machine, ticks it through the block's own ticker, and checks the item. */
  static void check(ServerLevel level) {
    var block = BuiltInRegistries.BLOCK.get(MACHINE);
    if (!(block instanceof MaterialEntityBlock<?>))
      throw new AssertionError("Standalone machine block missing: " + block);
    var holder = registrar.blockEntityTypes().get(MACHINE);
    if (holder == null || BuiltInRegistries.BLOCK_ENTITY_TYPE.get(MACHINE) != holder.get())
      throw new AssertionError("Standalone machine block-entity type missing");
    var pos = new BlockPos(0, level.getMaxBuildHeight() - 40, 0);
    level.getChunkAt(pos);
    level.setBlock(pos, block.defaultBlockState(), 3);
    if (!(level.getBlockEntity(pos) instanceof Counter counter))
      throw new AssertionError("Machine has no counter: " + level.getBlockEntity(pos));
    var state = level.getBlockState(pos);
    var ticker = state.getTicker(level, holder.get());
    if (ticker == null) throw new AssertionError("Server machine has no ticker");
    if (state.getTicker(level, BlockEntityType.FURNACE) != null)
      throw new AssertionError("Ticker installed for another block-entity type");
    @SuppressWarnings("unchecked")
    var typed = (BlockEntityTicker<BlockEntity>) ticker;
    int before = TICKS.get();
    typed.tick(level, pos, state, counter);
    // block_entity_data overrode the module's step of 1.
    if (TICKS.get() - before != 3) throw new AssertionError("Wrong machine settings");
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    var item = BuiltInRegistries.ITEM.get(ITEM);
    if (registrar.items().get(ITEM) == null || registrar.items().get(ITEM).get() != item)
      throw new AssertionError("Standalone item missing: " + item);
    if (BuiltInRegistries.ITEM.get(MACHINE) == net.minecraft.world.item.Items.AIR)
      throw new AssertionError("Standalone machine has no block item");
  }

  private static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
  }

  /** A block entity that counts its own ticks by its configured step. */
  public static final class Counter extends BlockEntity
      implements zone.moddev.mc.lib.gui.runtime.ScreenHost {
    public int clicks, guiReads;
    public final zone.moddev.mc.lib.api.capabilities.ItemStorage items =
        new zone.moddev.mc.lib.api.capabilities.ItemStorage(4);
    public final java.util.Map<String, zone.moddev.mc.lib.api.capabilities.SideConfig> sides =
        java.util.Map.of(
            "input",
                new zone.moddev.mc.lib.api.capabilities.SideConfig(
                    zone.moddev.mc.lib.api.capabilities.IoMode.INPUT),
            "water",
                new zone.moddev.mc.lib.api.capabilities.SideConfig(
                    zone.moddev.mc.lib.api.capabilities.IoMode.BOTH),
            "energy",
                new zone.moddev.mc.lib.api.capabilities.SideConfig(
                    zone.moddev.mc.lib.api.capabilities.IoMode.INPUT));
    public net.minecraft.world.item.ItemStack ghost = net.minecraft.world.item.ItemStack.EMPTY;

    public java.math.BigInteger getGuiEnergy() {
      guiReads++;
      return java.math.BigInteger.TEN.pow(100).multiply(java.math.BigInteger.valueOf(3));
    }

    public java.math.BigInteger getGuiCapacity() {
      return java.math.BigInteger.TEN.pow(101);
    }

    public int getGuiWaterCapacity() {
      return 1000;
    }

    public net.neoforged.neoforge.fluids.FluidStack getGuiWater() {
      return new net.neoforged.neoforge.fluids.FluidStack(
          net.minecraft.world.level.material.Fluids.WATER, 500);
    }

    public boolean isGuiEnabled() {
      return true;
    }

    public int getGuiMode() {
      return 1;
    }

    public java.util.List<net.minecraft.network.chat.Component> getGuiWarnings() {
      return java.util.List.of(
          net.minecraft.network.chat.Component.translatableWithFallback(
              "test.warning", "A live warning"));
    }

    public net.minecraft.network.chat.Component getGuiError() {
      return net.minecraft.network.chat.Component.translatableWithFallback(
          "test.error", "A live error");
    }

    public net.neoforged.neoforge.items.IItemHandlerModifiable screenItems() {
      return items;
    }

    public java.util.Map<String, zone.moddev.mc.lib.api.capabilities.SideConfig> screenSides() {
      return sides;
    }

    public boolean setScreenGhost(
        net.minecraft.world.entity.player.Player player,
        int slot,
        net.minecraft.world.item.ItemStack stack) {
      if (slot != 2) return false;
      ghost = stack;
      return true;
    }

    public net.minecraft.world.item.ItemStack screenGhost(int slot) {
      return ghost;
    }

    public int[] screenRecipeSlots(ResourceLocation category) {
      return new int[] {0};
    }

    Counter(BlockEntityType<Counter> type, BlockPos pos, BlockState state) {
      super(type, pos, state);
    }
  }

  private static final class CounterFactory implements BlockEntityFactory<Counter, Integer> {
    @Override
    public Class<Counter> blockEntityClass() {
      return Counter.class;
    }

    @Override
    public Codec<Integer> settingsCodec() {
      return Codec.intRange(1, 16).fieldOf("step").codec();
    }

    @Override
    public Counter create(
        BlockEntityType<Counter> type,
        BlockPos position,
        BlockState state,
        MMDBlockEntity definition,
        Integer step) {
      return new Counter(type, position, state);
    }

    @Override
    public BlockEntityTicker<Counter> ticker(Level level, MMDBlockEntity definition, Integer step) {
      return level.isClientSide() ? null : (world, pos, state, entity) -> TICKS.addAndGet(step);
    }
  }
}
