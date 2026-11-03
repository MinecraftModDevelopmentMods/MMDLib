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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
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
import zone.moddev.mc.lib.machine.inventory.SlotPlan;
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

  /** Every row of the reservations tables, driven through a real menu. */
  static void menuRows(ServerLevel level, BlockPos pos, ServerPlayer player) {
    var crusher = place(level, pos);
    var menu = new MachineMenu(81, player.getInventory(), crusher);
    var items = crusher.items;
    var iron = Items.IRON_INGOT;
    var gold = Items.GOLD_INGOT;
    var takeOne = new SlotPlan(1, ItemStack.EMPTY);
    // Menu slots: the machine's four, then the inventory's three rows, then the hotbar.
    int hotbar = 4 + 27;

    // Taking items out.
    reset(menu, player, items, stack(iron, 50), takeOne);
    menu.clicked(0, 0, ClickType.PICKUP, player);
    require(menu.getCarried().getCount() == 49 && count(items) == 1, "left click");

    reset(menu, player, items, stack(iron, 50), takeOne);
    menu.clicked(0, 1, ClickType.PICKUP, player);
    require(menu.getCarried().getCount() == 25 && count(items) == 25, "right click");

    reset(menu, player, items, stack(iron, 50), takeOne);
    menu.clicked(0, 0, ClickType.THROW, player);
    require(dropped(level, player) == 1 && count(items) == 49, "Q");
    reset(menu, player, items, stack(iron, 50), takeOne);
    menu.clicked(0, 1, ClickType.THROW, player);
    require(dropped(level, player) == 49 && count(items) == 1, "Ctrl+Q");

    reset(menu, player, items, stack(iron, 50), takeOne);
    menu.setCarried(stack(iron, 10));
    menu.clicked(4, 0, ClickType.PICKUP_ALL, player);
    require(menu.getCarried().getCount() == 59 && count(items) == 1, "double click");

    reset(menu, player, items, stack(iron, 50), takeOne);
    menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
    require(inventoryCount(player, iron) == 49 && count(items) == 1, "shift-click");
    menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
    require(inventoryCount(player, iron) == 49 && count(items) == 1, "second shift-click");

    reset(menu, player, items, stack(iron, 50), takeOne);
    menu.setCarried(stack(gold, 5));
    menu.clicked(0, 0, ClickType.PICKUP, player);
    require(count(items) == 50 && items.getStackInSlot(0).is(iron), "gold on iron: slot");
    require(menu.getCarried().is(gold) && menu.getCarried().getCount() == 5, "gold stays carried");

    reset(menu, player, items, stack(iron, 50), takeOne);
    menu.clicked(0, 2, ClickType.SWAP, player);
    require(count(items) == 50 && player.getInventory().getItem(2).isEmpty(), "swap, empty hotbar");
    require(totalIron(player, items) == 50, "swap duplicates nothing");
    player.getInventory().setItem(2, stack(gold, 3));
    menu.clicked(0, 2, ClickType.SWAP, player);
    require(count(items) == 50 && player.getInventory().getItem(2).is(gold), "swap, gold");

    // Putting items in.
    reset(menu, player, items, stack(iron, 50), takeOne);
    menu.setCarried(stack(iron, 64));
    menu.clicked(0, 0, ClickType.PICKUP, player);
    require(count(items) == 64 && menu.getCarried().getCount() == 50, "top up to 64");

    for (var carried : new Item[] {iron, gold}) {
      reset(menu, player, items, stack(iron, 1), new SlotPlan(1, stack(gold, 1)));
      menu.setCarried(stack(carried, 5));
      menu.clicked(0, 0, ClickType.PICKUP, player);
      require(count(items) == 1 && menu.getCarried().getCount() == 5, "jam, carrying " + carried);
      require(items.getStackInSlot(0).is(iron), "jam keeps the iron");
    }

    reset(menu, player, items, stack(iron, 63), new SlotPlan(1, stack(iron, 2)));
    menu.setCarried(stack(iron, 5));
    menu.clicked(0, 0, ClickType.PICKUP, player);
    require(count(items) == 63 && menu.getCarried().getCount() == 5, "full at the end");

    reset(menu, player, items, ItemStack.EMPTY, new SlotPlan(0, stack(gold, 1)));
    menu.setCarried(stack(gold, 64));
    menu.clicked(0, 0, ClickType.PICKUP, player);
    require(count(items) == 63 && menu.getCarried().getCount() == 1, "empty, gold in");
    reset(menu, player, items, ItemStack.EMPTY, new SlotPlan(0, stack(gold, 1)));
    menu.setCarried(stack(iron, 64));
    menu.clicked(0, 0, ClickType.PICKUP, player);
    require(items.getStackInSlot(0).isEmpty() && menu.getCarried().getCount() == 64, "empty, iron");

    // Shift-click from the player.
    reset(menu, player, items, stack(iron, 50), takeOne);
    player.getInventory().setItem(0, stack(iron, 64));
    menu.clicked(hotbar, 0, ClickType.QUICK_MOVE, player);
    require(count(items) == 64 && player.getInventory().getItem(0).getCount() == 50, "shift in");
    reset(menu, player, items, ItemStack.EMPTY, new SlotPlan(0, stack(gold, 1)));
    player.getInventory().setItem(0, stack(gold, 64));
    player.getInventory().setItem(1, stack(iron, 64));
    menu.clicked(hotbar, 0, ClickType.QUICK_MOVE, player);
    menu.clicked(hotbar + 1, 0, ClickType.QUICK_MOVE, player);
    require(count(items) == 63 && items.getStackInSlot(0).is(gold), "shift in, empty slot");
    require(player.getInventory().getItem(1).getCount() == 64, "iron stays out");

    // A drag across the slot and an inventory slot splits 64 into 32 each.
    reset(menu, player, items, stack(iron, 50), takeOne);
    menu.setCarried(stack(iron, 64));
    menu.clicked(-999, 0, ClickType.QUICK_CRAFT, player);
    menu.clicked(0, 1, ClickType.QUICK_CRAFT, player);
    menu.clicked(4, 1, ClickType.QUICK_CRAFT, player);
    menu.clicked(-999, 2, ClickType.QUICK_CRAFT, player);
    require(count(items) == 64, "drag, slot " + count(items));
    require(menu.getSlot(4).getItem().getCount() == 32, "drag, inventory");
    require(menu.getCarried().getCount() == 18, "drag, carried " + menu.getCarried().getCount());

    // Review focus 4: the reserved item can't be taken, but the same item still merges in.
    reset(menu, player, items, stack(iron, 1), takeOne);
    menu.setCarried(stack(iron, 10));
    menu.clicked(0, 0, ClickType.PICKUP, player);
    require(count(items) == 11 && menu.getCarried().isEmpty(), "merge onto the reserved one");
    reset(menu, player, items, stack(iron, 1), takeOne);
    menu.clicked(0, 0, ClickType.PICKUP, player);
    require(count(items) == 1 && menu.getCarried().isEmpty(), "nothing free, nothing moves");

    // No plan: swap, number key and whole-stack shift-click work as before.
    reset(menu, player, items, stack(iron, 50), SlotPlan.NONE);
    player.getInventory().setItem(2, stack(gold, 3));
    menu.clicked(0, 2, ClickType.SWAP, player);
    require(items.getStackInSlot(0).is(gold) && player.getInventory().getItem(2).is(iron), "swap");
    reset(menu, player, items, stack(iron, 50), SlotPlan.NONE);
    menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
    require(count(items) == 0 && inventoryCount(player, iron) == 50, "whole-stack shift-click");

    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
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

  /** Puts {@code held} in slot 0 under {@code plan}, with empty hands and no stray drops. */
  private static void reset(
      MachineMenu menu, ServerPlayer player, ItemStorage items, ItemStack held, SlotPlan plan) {
    menu.setCarried(ItemStack.EMPTY);
    player.getInventory().clearContent();
    var area = player.getBoundingBox().inflate(8);
    for (var drop : player.serverLevel().getEntitiesOfClass(ItemEntity.class, area)) drop.discard();
    items.setPlan(0, SlotPlan.NONE);
    items.setStackInSlot(0, held);
    items.setPlan(0, plan);
  }

  private static ItemStack stack(Item item, int count) {
    return new ItemStack(item, count);
  }

  private static int count(ItemStorage items) {
    return items.getStackInSlot(0).getCount();
  }

  private static int dropped(ServerLevel level, ServerPlayer player) {
    int total = 0;
    var area = player.getBoundingBox().inflate(8);
    for (var drop : level.getEntitiesOfClass(ItemEntity.class, area))
      total += drop.getItem().getCount();
    return total;
  }

  private static int inventoryCount(ServerPlayer player, Item item) {
    int total = 0;
    var inventory = player.getInventory();
    for (int i = 0; i < inventory.getContainerSize(); i++)
      if (inventory.getItem(i).is(item)) total += inventory.getItem(i).getCount();
    return total;
  }

  /** Every iron ingot the player and the slot hold between them. */
  private static int totalIron(ServerPlayer player, ItemStorage items) {
    return inventoryCount(player, Items.IRON_INGOT)
        + (items.getStackInSlot(0).is(Items.IRON_INGOT) ? count(items) : 0);
  }

  private static void require(boolean condition, String what) {
    if (!condition) throw new AssertionError(what);
  }
}
