package zone.moddev.mc.lib.smoke;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
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
import net.neoforged.neoforge.items.SlotItemHandler;

import zone.moddev.mc.lib.api.MaterialEventTypes;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.MachineCapabilities;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.common.factories.BlockEntityFactory;
import zone.moddev.mc.lib.common.runtime.DropsContents;
import zone.moddev.mc.lib.common.types.MMDBlockEntity;
import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.load.ScreenBinder;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.runtime.ClientMenuItems;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.runtime.ScreenNetwork;
import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;
import zone.moddev.mc.lib.machine.feature.FeatureHost;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor.Status;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;
import zone.moddev.mc.lib.machine.neoforge.FeatureSync;
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

  /** Item storage whose extraction a test can cap, like a subclass that gives less than asked. */
  public static final class Capped extends ItemStorage {
    int cap = Integer.MAX_VALUE;

    Capped(int slots) {
      super(slots);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
      return super.extractItem(slot, Math.min(amount, cap), simulate);
    }
  }

  private static Capped capped(Runnable onChange) {
    var items = new Capped(4);
    items.role(Role.INPUT, 0).role(Role.OUTPUT, 1, 2, 3).onChange(onChange);
    return items;
  }

  /** Ore in slot 0, products in 1 and 2, the byproduct in 3, water and energy beside them. */
  public static final class Crusher extends BlockEntity implements ScreenHost, DropsContents, Clearable {
    public final Capped items = capped(this::setChanged);
    public IItemHandlerModifiable menuItems = items;
    public final FluidStorage fluids = new FluidStorage().onChange(this::setChanged);
    public final EnergyBuffer energy =
        new EnergyBuffer(10_000_000_000L, 1_000).onChange(this::setChanged);
    public final FeatureHost host =
        new FeatureHost(this::setChanged, () -> FeatureSync.send(this));
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

    @Override
    public void dropContents(Level level, BlockPos pos) {
      items.dropAll(level, pos);
    }

    @Override
    public void clearContent() {
      for (int slot = 0; slot < items.getSlots(); slot++) {
        items.setPlan(slot, SlotPlan.NONE);
        items.setStackInSlot(slot, ItemStack.EMPTY);
      }
    }

    @Override
    public IItemHandlerModifiable screenItems() {
      return menuItems;
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
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
      return FeatureSync.updateTag(host, registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
      super.loadAdditional(tag, registries);
      if (FeatureSync.read(host, tag, registries)) return;
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
    require(count(items) == 1 && player.getInventory().getItem(2).getCount() == 49,
        "swap moves only unreserved items to an empty hotbar slot");
    require(totalIron(player, items) == 50, "swap duplicates nothing");
    reset(menu, player, items, stack(iron, 50), takeOne);
    player.getInventory().setItem(2, stack(gold, 3));
    menu.clicked(0, 2, ClickType.SWAP, player);
    require(count(items) == 50 && player.getInventory().getItem(2).is(gold), "swap, gold");

    reset(menu, player, items, stack(iron, 50), takeOne);
    player.getInventory().setItem(2, stack(iron, 20));
    menu.clicked(0, 2, ClickType.SWAP, player);
    require(count(items) == 21 && player.getInventory().getItem(2).getCount() == 49,
        "same-item swap preserves the reserved portion");
    require(totalIron(player, items) == 70, "same-item swap conserves items");
    reset(menu, player, items, stack(iron, 50), takeOne);
    player.getInventory().setItem(40, ItemStack.EMPTY);
    menu.clicked(0, 40, ClickType.SWAP, player);
    require(count(items) == 1 && player.getInventory().getItem(40).getCount() == 49,
        "offhand swap moves only the free portion");
    player.getInventory().setItem(40, ItemStack.EMPTY);

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

  /** Wrapped storages publish, predict and release reservations just like built-in storages. */
  static void wrappedReservations(ServerLevel level, BlockPos pos, ServerPlayer player) {
    var crusher = place(level, pos);
    crusher.menuItems = new WrappedReservedItems(crusher.items);
    crusher.items.setStackInSlot(0, stack(Items.IRON_INGOT, 10));
    var plan = new SlotPlan(3, ItemStack.EMPTY);
    crusher.items.setPlan(0, plan);
    var server = new MachineMenu(85, player.getInventory(), crusher);
    var client = new MachineMenu(86, player.getInventory(), crusher, true) {};
    client.getSlot(0).set(stack(Items.IRON_INGOT, 10));
    var snapshot = server.reservationSnapshot();
    require(snapshot.get(MachineMenu.PLAN_KEYS) instanceof SlotPlan sent && sent.sameAs(plan),
        "Wrapped storage publishes its reservation");
    client.apply(snapshot);
    server.clicked(0, 0, ClickType.PICKUP, player);
    client.clicked(0, 0, ClickType.PICKUP, player);
    require(server.getCarried().getCount() == 7 && client.getCarried().getCount() == 7,
        "Both sides take only the free portion");
    require(server.getSlot(0).getItem().getCount() == 3 && client.getSlot(0).getItem().getCount() == 3,
        "Both sides display the reserved remainder outside interactions");
    crusher.items.setPlan(0, SlotPlan.NONE);
    client.apply(zone.moddev.mc.lib.gui.runtime.ScreenValues.changed(
        snapshot, server.reservationSnapshot(), false));
    client.setCarried(ItemStack.EMPTY);
    client.clicked(0, 0, ClickType.PICKUP, player);
    require(client.getCarried().getCount() == 3, "A released wrapper plan reaches the client");
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
  }

  /** A command that clears a block entity before replacing it leaves nothing to drop. */
  static void clearableDropsNothing(ServerLevel level, BlockPos pos) {
    var crusher = place(level, pos);
    crusher.items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 5));
    crusher.items.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 5));
    Clearable.tryClear(crusher);
    level.destroyBlock(pos, false);
    int dropped = 0;
    for (var drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)))
      dropped += drop.getItem().getCount();
    require(dropped == 0, "a cleared crusher dropped " + dropped);
  }

  /** Shift-clicking a planned slot never gives the player more than the slot lost. */
  static void shiftClickShortfalls(ServerLevel level, BlockPos pos, ServerPlayer player) {
    var crusher = place(level, pos);
    var menu = new MachineMenu(84, player.getInventory(), crusher);
    var items = crusher.items;
    var iron = Items.IRON_INGOT;
    var takeOne = new SlotPlan(1, ItemStack.EMPTY);

    // Only part of the 49 fits: 24 more iron on a stack of 40, every other slot full of gold.
    reset(menu, player, items, stack(iron, 50), takeOne);
    for (int i = 0; i < 36; i++) player.getInventory().setItem(i, stack(Items.GOLD_INGOT, 64));
    player.getInventory().setItem(7, stack(iron, 40));
    menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
    require(inventoryCount(player, iron) == 64, "24 moved: " + inventoryCount(player, iron));
    require(count(items) == 26, "the slot kept the rest: " + count(items));
    require(totalIron(player, items) == 90, "partial fit conserves iron");

    // A storage that gives 10 at a time: the click repeats, and the player gets what the slot lost.
    reset(menu, player, items, stack(iron, 50), takeOne);
    items.cap = 10;
    try {
      menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
    } finally {
      items.cap = Integer.MAX_VALUE;
    }
    require(inventoryCount(player, iron) == 49, "49 moved: " + inventoryCount(player, iron));
    require(count(items) == 1, "the slot kept the reserved one: " + count(items));
    require(totalIron(player, items) == 50, "a short extract duplicates nothing");

    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
  }

  /** One click on slot 0 with a given slot, plan, carried stack and hotbar. */
  private record Row(
      String name,
      ItemStack held,
      SlotPlan plan,
      ItemStack carried,
      ItemStack hotbar,
      BiConsumer<MachineMenu, ServerPlayer> click) {}

  /**
   * Runs the menu rows through the server's menu and through one built the way a client builds it,
   * over {@link ClientMenuItems}, and requires the same slot, carried stack, inventory and drops
   * after each: the client predicts every click without a correction from the server.
   */
  static void clientRows(ServerLevel level, BlockPos pos, ServerPlayer player) {
    var crusher = place(level, pos);
    var server = new MachineMenu(82, player.getInventory(), crusher);
    var client = new MachineMenu(83, player.getInventory(), crusher, true) {};
    var clientItems = (ClientMenuItems) ((SlotItemHandler) client.getSlot(0)).getItemHandler();
    var iron = Items.IRON_INGOT;
    var gold = Items.GOLD_INGOT;
    var none = ItemStack.EMPTY;
    var takeOne = new SlotPlan(1, none);
    var jam = new SlotPlan(1, stack(gold, 1));
    int hotbar = 4 + 27;
    var rows = new java.util.ArrayList<Row>();
    for (var click :
        new Object[][] {
          {"left", 0, ClickType.PICKUP},
          {"right", 1, ClickType.PICKUP},
          {"Q", 0, ClickType.THROW},
          {"Ctrl+Q", 1, ClickType.THROW},
          {"shift", 0, ClickType.QUICK_MOVE},
          {"swap", 2, ClickType.SWAP},
        }) {
      var type = (ClickType) click[2];
      int button = (Integer) click[1];
      BiConsumer<MachineMenu, ServerPlayer> action = (m, p) -> m.clicked(0, button, type, p);
      var name = click[0] + ": ";
      var hotbarGold = stack(gold, 3);
      rows.add(new Row(name + "50 iron take 1", stack(iron, 50), takeOne, none, none, action));
      rows.add(new Row(name + "1 iron take 1", stack(iron, 1), takeOne, none, none, action));
      rows.add(new Row(name + "2 iron, add gold", stack(iron, 2), jam, none, none, action));
      rows.add(new Row(name + "no plan", stack(iron, 50), SlotPlan.NONE, none, none, action));
      rows.add(new Row(name + "gold in hotbar", stack(iron, 50), takeOne, none, hotbarGold, action));
    }
    BiConsumer<MachineMenu, ServerPlayer> place =
        (m, p) -> m.clicked(0, 0, ClickType.PICKUP, p);
    var addIron = new SlotPlan(1, stack(iron, 2));
    var addGold = new SlotPlan(0, stack(gold, 1));
    for (var carried :
        new ItemStack[] {stack(iron, 64), stack(iron, 10), stack(gold, 64), stack(gold, 5)}) {
      var name = "carrying " + carried.getCount() + " " + carried.getItem() + ": ";
      rows.add(new Row(name + "50 iron", stack(iron, 50), takeOne, carried, none, place));
      rows.add(new Row(name + "1 iron take 1", stack(iron, 1), takeOne, carried, none, place));
      rows.add(new Row(name + "jam", stack(iron, 1), jam, carried, none, place));
      rows.add(new Row(name + "63 iron, add 2", stack(iron, 63), addIron, carried, none, place));
      rows.add(new Row(name + "empty, add gold", none, addGold, carried, none, place));
      rows.add(new Row(name + "empty, no plan", none, SlotPlan.NONE, carried, none, place));
    }
    BiConsumer<MachineMenu, ServerPlayer> doubleClick =
        (m, p) -> m.clicked(4, 0, ClickType.PICKUP_ALL, p);
    BiConsumer<MachineMenu, ServerPlayer> shiftIn =
        (m, p) -> m.clicked(hotbar + 2, 0, ClickType.QUICK_MOVE, p);
    BiConsumer<MachineMenu, ServerPlayer> drag =
        (m, p) -> {
          m.clicked(-999, 0, ClickType.QUICK_CRAFT, p);
          m.clicked(0, 1, ClickType.QUICK_CRAFT, p);
          m.clicked(4, 1, ClickType.QUICK_CRAFT, p);
          m.clicked(-999, 2, ClickType.QUICK_CRAFT, p);
        };
    rows.add(new Row("double click", stack(iron, 50), takeOne, stack(iron, 10), none, doubleClick));
    rows.add(new Row("shift in", stack(iron, 50), takeOne, none, stack(iron, 64), shiftIn));
    rows.add(new Row("shift in, empty", none, addGold, none, stack(gold, 64), shiftIn));
    rows.add(new Row("shift in, wrong item", none, addGold, none, stack(iron, 64), shiftIn));
    rows.add(new Row("shift in, no plan", none, SlotPlan.NONE, none, stack(iron, 64), shiftIn));
    rows.add(new Row("drag", stack(iron, 50), takeOne, stack(iron, 64), none, drag));

    for (var row : rows) {
      crusher.items.setPlan(0, SlotPlan.NONE);
      crusher.items.setStackInSlot(0, ItemStack.EMPTY);
      var onServer = outcome(server, player, row, crusher.items, null);
      crusher.items.setPlan(0, SlotPlan.NONE);
      crusher.items.setStackInSlot(0, ItemStack.EMPTY);
      var onClient = outcome(client, player, row, null, clientItems);
      require(
          onServer.equals(onClient),
          row.name() + ": server " + onServer + ", client " + onClient);
    }
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
  }

  /** Sets the row up on one menu, clicks, and describes everything the click could have moved. */
  private static String outcome(
      MachineMenu menu, ServerPlayer player, Row row, ItemStorage server, ClientMenuItems client) {
    menu.setCarried(row.carried().copy());
    player.getInventory().clearContent();
    player.getInventory().setItem(2, row.hotbar().copy());
    var area = player.getBoundingBox().inflate(8);
    for (var drop : player.serverLevel().getEntitiesOfClass(ItemEntity.class, area)) drop.discard();
    if (server != null) {
      server.setStackInSlot(0, row.held().copy());
      server.setPlan(0, row.plan());
    } else {
      client.setStackInSlot(0, row.held().copy());
      client.setPlan(0, row.plan());
    }
    row.click().accept(menu, player);
    var text = new StringBuilder(menu.getSlot(0).getItem() + ", carried " + menu.getCarried());
    var inventory = player.getInventory();
    for (int i = 0; i < inventory.getContainerSize(); i++)
      if (!inventory.getItem(i).isEmpty())
        text.append(", slot ").append(i).append(' ').append(inventory.getItem(i));
    return text + ", dropped " + dropped(player.serverLevel(), player);
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
    require(!crusher.processor.paused(), "resumed");
    crusher.host.tick(level);
    require(crusher.items.plan(0).take() == 1, "the cycle reserves again");
    require(crusher.getProgress() > progress, "progress carried on: " + crusher.getProgress());
    player.containerMenu = previous;

    // Break the block with the cycle live: the ore it holds comes out, and nothing else.
    require(crusher.items.getStackInSlot(0).getCount() == 1, "one ore inside");
    level.destroyBlock(pos, false);
    int dropped = 0;
    for (var drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2))) {
      require(drop.getItem().is(Items.COPPER_ORE), "dropped " + drop.getItem());
      dropped += drop.getItem().getCount();
    }
    level.setBlock(above, Blocks.AIR.defaultBlockState(), 3);
    require(dropped == 1, "one ore went in and one came out: " + dropped);
  }

  /** Gives a crusher what it needs to run the copper ore recipe. */
  static void fill(Crusher crusher) {
    crusher.items.setStackInSlot(0, new ItemStack(Items.COPPER_ORE, 1));
    crusher.fluids.tank(0).fill(new FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE);
    crusher.energy.setStored(100_000);
  }

  /** Requires a client's copy of a running crusher to know the cycle's time and hold no plans. */
  static void requireClientCopy(Crusher client) {
    require(client.processor.time() == 200, "client time " + client.processor.time());
    for (int slot = 0; slot < 4; slot++)
      require(client.items.plan(slot).isNone(), "the client holds a plan on slot " + slot);
    require(client.fluids.plan(0).isNone(), "the client holds a tank plan");
  }

  /** What a player near a crusher is sent, read into a copy that has no level. */
  static void syncRoundTrip(ServerLevel level, BlockPos pos) {
    var registries = level.registryAccess();

    // Each status arrives, with the time right and the progress as of the send.
    var running = fresh(pos, 3, 100_000);
    for (int tick = 0; tick < 51; tick++) running.host.tick(level);
    var copy = fresh(pos, 0, 0);
    // The copy holds the same ore; a running cycle's update still gives it no plans.
    copy.items.setStackInSlot(0, new ItemStack(Items.COPPER_ORE, 3));
    copy.fluids.tank(0).fill(new FluidStack(Fluids.WATER, 123), FluidAction.EXECUTE);
    int water = copy.fluids.getFluidInTank(0).getAmount();
    copy.energy.setStored(777);
    var update = update(level, running);
    for (var storage : List.of("items", "fluids", "energy", "features"))
      require(!update.contains(storage), "the update has " + storage + ": " + update);
    copy.loadWithComponents(update, registries);
    arrived(copy, Status.RUNNING, 50, 200, "running");
    for (int slot = 0; slot < 4; slot++)
      require(copy.items.plan(slot).isNone(), "the copy holds a plan on slot " + slot);
    require(copy.fluids.plan(0).isNone(), "the copy holds a tank plan");
    require(copy.items.getStackInSlot(0).getCount() == 3, "items were left alone");
    require(copy.fluids.getFluidInTank(0).getAmount() == water, "fluids were left alone");
    require(copy.energy.stored() == 777, "energy was left alone");

    var stalled = fresh(pos, 3, 100_000);
    for (int tick = 0; tick < 31; tick++) stalled.host.tick(level);
    stalled.energy.setStored(0);
    stalled.host.tick(level);
    copy.loadWithComponents(update(level, stalled), registries);
    arrived(copy, Status.STALLED, 30, 200, "stalled");

    var blocked = fresh(pos, 3, 100_000);
    for (int tick = 0; tick < 101; tick++) blocked.host.tick(level);
    blocked.items.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
    for (int tick = 0; tick < 100; tick++) blocked.host.tick(level);
    copy.loadWithComponents(update(level, blocked), registries);
    arrived(copy, Status.BLOCKED, 200, 200, "blocked");

    var paused = fresh(pos, 3, 100_000);
    for (int tick = 0; tick < 41; tick++) paused.host.tick(level);
    paused.processor.interrupt();
    paused.host.tick(level);
    copy.loadWithComponents(update(level, paused), registries);
    arrived(copy, Status.PAUSED, 40, 0, "paused");

    copy.loadWithComponents(update(level, fresh(pos, 0, 0)), registries);
    arrived(copy, Status.IDLE, 0, 0, "idle");

    // A bad update keeps the last status and doesn't throw. The copy is stalled, the update says
    // running, and the status name in it is garbage.
    copy.loadWithComponents(update(level, stalled), registries);
    arrived(copy, Status.STALLED, 30, 200, "before the bad update");
    var bad = update(level, running);
    boolean mutated = false;
    for (var key : bad.getAllKeys())
      if (bad.get(key) instanceof CompoundTag features && features.contains("work")) {
        features.getCompound("work").putString("status", "NO_SUCH_STATUS");
        mutated = true;
      }
    require(mutated, "the update had no processor state to spoil");
    copy.loadWithComponents(bad, registries);
    arrived(copy, Status.STALLED, 50, 200, "bad update");

    // A save is still a save: it restores the cycle and its plans.
    var saved = running.saveWithFullMetadata(registries);
    var restored = fresh(pos, 0, 0);
    require(!FeatureSync.read(restored.host, saved, registries), "a save read as an update");
    restored.loadWithComponents(saved, registries);
    arrived(restored, Status.RUNNING, 50, 200, "saved");
    require(restored.items.plan(0).take() == 1, "the saved cycle's plans came back");

    // Making the update runs neither callback.
    int[] calls = new int[2];
    var spy = new FeatureHost(() -> calls[0]++, () -> calls[1]++).add(running.processor);
    FeatureSync.updateTag(spy, registries);
    require(calls[0] == 0 && calls[1] == 0, "getUpdateTag ran a callback");
  }

  /** The update as a player is sent it: the tag of the block entity's data packet. */
  private static CompoundTag update(ServerLevel level, Crusher crusher) {
    // The packet asks the block entity's level for the registries.
    crusher.setLevel(level);
    return ClientboundBlockEntityDataPacket.create(crusher).getTag();
  }

  private static void arrived(Crusher copy, Status status, int progress, int time, String what) {
    var processor = copy.processor;
    boolean right =
        processor.status() == status
            && processor.progress() == progress
            && processor.time() == time;
    require(
        right,
        what + ": " + processor.status() + " at " + processor.progress() + "/" + processor.time());
  }

  /** A crusher with no level, holding {@code ore} copper ore, water and {@code fe} energy. */
  @SuppressWarnings("unchecked")
  private static Crusher fresh(BlockPos pos, int ore, long fe) {
    var type = (BlockEntityType<Crusher>) MachineSmoke.registrar().blockEntityTypes().get(ID).get();
    var crusher = new Crusher(type, pos, BuiltInRegistries.BLOCK.get(ID).defaultBlockState());
    if (ore > 0) crusher.items.setStackInSlot(0, new ItemStack(Items.COPPER_ORE, ore));
    crusher.fluids.tank(0).fill(new FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE);
    crusher.energy.setStored(fe);
    return crusher;
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
