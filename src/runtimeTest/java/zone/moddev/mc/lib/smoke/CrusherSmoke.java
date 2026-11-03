package zone.moddev.mc.lib.smoke;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import zone.moddev.mc.lib.api.MaterialEventTypes;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.MachineCapabilities;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.common.factories.BlockEntityFactory;
import zone.moddev.mc.lib.common.types.MMDBlockEntity;
import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.runtime.ScreenNetwork;
import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;
import zone.moddev.mc.lib.machine.feature.FeatureHost;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.neoforge.StorageGroups;

/**
 * The example crusher of the machine module: a block entity with storages, a recipe processor and a
 * screen, and the checks that drive its menu the way a player does.
 */
@EventBusSubscriber(modid = "mmdlib", bus = EventBusSubscriber.Bus.MOD)
public final class CrusherSmoke {
  static final ResourceLocation ID = ResourceLocation.parse("mmdlib_smoke:crusher");
  static final ResourceLocation ENTITY_FACTORY =
      ResourceLocation.parse("mmdlib_smoke:crusher_entity");
  private static final ResourceLocation INTERRUPT =
      ResourceLocation.parse("mmdlib_smoke:crusher_interrupt");

  private CrusherSmoke() {}

  @SubscribeEvent
  static void capabilities(RegisterCapabilitiesEvent event) {
    @SuppressWarnings("unchecked")
    var type = (BlockEntityType<Crusher>) MachineSmoke.registrar().blockEntityTypes().get(ID).get();
    MachineCapabilities.items(event, type, crusher -> crusher.items);
  }

  /** Registers the interrupt button's handler and the screen; called once at startup. */
  static void install() {
    MaterialExtensions.GLOBAL
        .events()
        .registerCallback(
            INTERRUPT,
            MaterialEventTypes.GUI_CLICK,
            click -> ((Crusher) click.menu().blockEntity).toggleInterrupt());
    String xml =
        """
<screen xmlns="https://moddev.zone/mmdlib/screen" title="crusher.title" title-fallback="Crusher"
        block-entity="zone.moddev.mc.lib.smoke.CrusherSmoke$Crusher">
  <vbox padding="7" spacing="4">
    <hbox spacing="6">
      <slot index="0"/>
      <progress id="progress" value="#getProgress" max="#getMaxProgress"/>
      <slot index="1"/>
      <slot index="2"/>
      <slot index="3"/>
    </hbox>
    <button id="interrupt" text="crusher.interrupt" text-fallback="Interrupt" tooltip="crusher.interrupt.tip" tooltip-fallback="Pause the crusher and free its items" on-click="mmdlib_smoke:crusher_interrupt"/>
  </vbox>
</screen>
""";
    try {
      var screen =
          ScreenReader.read(
              ID, "crusher.xml", new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      var bound = ScreenBinder.bind(screen, CrusherSmoke.class.getClassLoader());
      var all = new HashMap<>(Screens.all());
      all.put(ID, bound);
      Screens.install(all);
      ScreenRuntime.validate();
    } catch (java.io.IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** Ore in slot 0, products in 1 and 2, the byproduct in 3, water and energy beside them. */
  public static final class Crusher extends BlockEntity implements ScreenHost {
    public final ItemStorage items =
        new ItemStorage(4)
            .role(Role.INPUT, 0)
            .role(Role.OUTPUT, 1, 2, 3)
            .onChange(this::setChanged);
    public final FluidStorage fluids = new FluidStorage().onChange(this::setChanged);
    public final EnergyBuffer energy =
        new EnergyBuffer(10_000_000_000L, 1_000).onChange(this::setChanged);
    public final FeatureHost host = new FeatureHost(this::setChanged);
    public final RecipeProcessor processor;

    Crusher(BlockEntityType<Crusher> type, BlockPos pos, BlockState state) {
      super(type, pos, state);
      fluids.addTank(4_000, Role.INPUT);
      var inventory =
          MachineInventory.builder()
              .group("input", StorageGroups.items(items, 0))
              .group("output", StorageGroups.items(items, 1, 2))
              .group("byproduct", StorageGroups.items(items, 3))
              .group("water", StorageGroups.fluids(fluids, 0))
              .group("energy", StorageGroups.energy(energy))
              .build();
      processor = new RecipeProcessor("work", MachineRecipeTypeSmoke.REFINING, inventory);
      host.add(processor);
    }

    public int getProgress() {
      return processor.progress();
    }

    public int getMaxProgress() {
      return processor.time();
    }

    void toggleInterrupt() {
      if (processor.paused()) processor.resume();
      else processor.interrupt();
    }

    /** What breaking the block does: free every reservation, then drop what is inside. */
    void dropContents(Level level) {
      processor.interrupt();
      items.dropAll(level, getBlockPos());
    }

    @Override
    public IItemHandlerModifiable screenItems() {
      return items;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
      super.saveAdditional(tag, registries);
      tag.put("items", items.serializeNBT(registries));
      tag.put("fluids", fluids.serializeNBT(registries));
      tag.put("energy", energy.serializeNBT(registries));
      tag.put("features", host.save(registries.createSerializationContext(NbtOps.INSTANCE)));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
      super.loadAdditional(tag, registries);
      // Storages first: the processor checks the loaded contents against its saved plan.
      items.deserializeNBT(registries, tag.getCompound("items"));
      fluids.deserializeNBT(registries, tag.getCompound("fluids"));
      energy.deserializeNBT(registries, tag.get("energy"));
      host.load(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("features"));
    }
  }

  /** Builds crushers and ticks their features on the server. */
  static final class Factory implements BlockEntityFactory<Crusher, Integer> {
    @Override
    public Class<Crusher> blockEntityClass() {
      return Crusher.class;
    }

    @Override
    public Codec<Integer> settingsCodec() {
      return Codec.unit(0);
    }

    @Override
    public Crusher create(
        BlockEntityType<Crusher> type,
        BlockPos position,
        BlockState state,
        MMDBlockEntity definition,
        Integer settings) {
      return new Crusher(type, position, state);
    }

    @Override
    public BlockEntityTicker<Crusher> ticker(
        Level level, MMDBlockEntity definition, Integer settings) {
      return level.isClientSide() ? null : (world, pos, state, crusher) -> crusher.host.tick(world);
    }
  }

  /** A hopper feeds a crusher, which saves, reloads and breaks in the middle of a cycle. */
  static void endToEnd(ServerLevel level, BlockPos pos, ServerPlayer player) {
    var crusher = place(level, pos);
    var above = pos.above();
    level.setBlock(above, Blocks.HOPPER.defaultBlockState(), 3);
    var hopper = (HopperBlockEntity) level.getBlockEntity(above);
    hopper.setItem(0, new ItemStack(Items.COPPER_ORE, 1));
    crusher.fluids.tank(0).fill(new FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE);
    crusher.energy.setStored(100_000);
    HopperBlockEntity.pushItemsTick(level, above, level.getBlockState(above), hopper);
    require(crusher.items.getStackInSlot(0).is(Items.COPPER_ORE), "the hopper delivered the ore");
    require(hopper.isEmpty(), "the hopper gave it up");

    for (int tick = 0; tick < 100; tick++) crusher.host.tick(level);
    int progress = crusher.getProgress();
    require(progress > 50 && progress < 200, "mid-cycle, at " + progress);
    require(crusher.items.plan(0).take() == 1, "the ore is reserved");

    var saved = crusher.saveWithFullMetadata(level.registryAccess());
    var loaded =
        BlockEntity.loadStatic(pos, level.getBlockState(pos), saved, level.registryAccess());
    require(loaded instanceof Crusher, "loaded " + loaded);
    var copy = (Crusher) loaded;
    require(copy.getProgress() == progress, "progress " + copy.getProgress() + "/" + progress);
    require(copy.getMaxProgress() == 200, "time " + copy.getMaxProgress());
    require(copy.items.plan(0).take() == 1, "the loaded crusher still reserves the ore");

    player.setPos(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5);
    var menu = new MachineMenu(82, player.getInventory(), crusher);
    var element = menu.bound.screen().find("progress").orElseThrow();
    int key = menu.index(element) * 2;
    var values = screenValues(crusher, menu);
    require(
        values.get(key) instanceof Number n && n.intValue() == progress,
        "screen progress " + values.get(key));
    require(
        values.get(key + 1) instanceof Number n && n.intValue() == 200,
        "screen maximum " + values.get(key + 1));

    // The interrupt button, through the validated action path.
    var previous = player.containerMenu;
    player.containerMenu = menu;
    int button = menu.elements.indexOf(menu.bound.screen().find("interrupt").orElseThrow());
    menu.clickMenuButton(player, new ScreenNetwork.Action(82, button, 0, 0, 0, 0).pack());
    require(crusher.processor.paused() && crusher.items.plan(0).isNone(), "interrupted");
    menu.clickMenuButton(player, new ScreenNetwork.Action(82, button, 0, 0, 0, 0).pack());
    require(!crusher.processor.paused() && crusher.getProgress() == progress, "resumed");
    player.containerMenu = previous;

    crusher.dropContents(level);
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    int dropped = 0;
    for (var drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)))
      dropped += drop.getItem().getCount();
    level.setBlock(above, Blocks.AIR.defaultBlockState(), 3);
    require(dropped == 1, "one ore went in and one came out: " + dropped);
  }

  /** What {@code ScreenSnapshots} reads off the crusher for the menu's screen. */
  @SuppressWarnings("unchecked")
  private static Map<Integer, Object> screenValues(Crusher crusher, MachineMenu menu) {
    try {
      var snapshots = Class.forName("zone.moddev.mc.lib.gui.runtime.ScreenSnapshots");
      var read = snapshots.getDeclaredMethod("read", BlockEntity.class, ScreenBinder.Bound.class);
      read.setAccessible(true);
      return (Map<Integer, Object>) read.invoke(null, crusher, menu.bound);
    } catch (ReflectiveOperationException ex) {
      throw new AssertionError(ex);
    }
  }

  private static Crusher place(ServerLevel level, BlockPos pos) {
    level.setBlock(pos, BuiltInRegistries.BLOCK.get(ID).defaultBlockState(), 3);
    return (Crusher) level.getBlockEntity(pos);
  }

  private static void require(boolean condition, String what) {
    if (!condition) throw new AssertionError(what);
  }
}
