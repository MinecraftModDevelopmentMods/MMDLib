package zone.moddev.mc.companion.test;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import net.neoforged.neoforge.gametest.*;

import zone.moddev.mc.companion.*;
import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.gui.runtime.*;

@GameTestHolder(CompanionMod.ID)
@PrefixGameTestTemplate(false)
public final class AutomationGameTests {
  private static ProcessorEntity place(GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(2, 2, 2));
    h.getLevel().setBlock(pos, CompanionMod.LOGISTICS.block.get().defaultBlockState(), 3);
    return (ProcessorEntity) h.getLevel().getBlockEntity(pos);
  }

  private static void require(boolean b, String s) {
    if (!b) throw new AssertionError(s);
  }

  @GameTest(template = "empty")
  public static void fullInventoryDropsConfiguredFilterOnce(GameTestHelper h) {
    var machine = place(h);
    var a = machine.automation;
    var player = h.makeMockServerPlayerInLevel();
    for (int i = 0; i < player.getInventory().items.size(); i++)
      player.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
    a.slots().setStackInSlot(0, new ItemStack(CompanionAddons.PULLER.get()));
    a.slots().setStackInSlot(4, new ItemStack(CompanionAddons.ITEM_FILTER.get()));
    long generation = a.generation(0, 0), revision = a.revision(0, 0);
    a.edit(
        0,
        0,
        generation,
        revision,
        "samples",
        0,
        "sample",
        new ItemStack(Items.DIAMOND),
        h.getLevel().registryAccess());
    // A same-type replacement still invalidates the editor and detaches its old child.
    a.slots().setStackInSlot(0, new ItemStack(CompanionAddons.PULLER.get()));
    require(
        !a.edit(
            0,
            0,
            generation,
            revision,
            "deny",
            0,
            "",
            ItemStack.EMPTY,
            h.getLevel().registryAccess()),
        "Old attachment cannot edit replacement");
    a.recover(player);
    a.recover(player);
    var drops =
        h.getLevel()
            .getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(machine.getBlockPos()).inflate(1),
                e -> e.getItem().is(CompanionAddons.ITEM_FILTER.get()));
    require(
        drops.size() == 1 && drops.getFirst().getItem().getCount() == 1,
        "Exactly one dropped filter");
    var filter = drops.getFirst().getItem();
    require(
        filter
            .getCapability(AddonCapabilities.FILTER)
            .matches(filter, new ItemStack(Items.DIAMOND), h.getLevel().registryAccess()),
        "Dropped filter keeps configuration");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void typedCustomFilterActionValidatesPayload(GameTestHelper h) {
    var machine = place(h);
    var a = machine.automation;
    a.slots().setStackInSlot(0, new ItemStack(CompanionAddons.PULLER.get()));
    a.slots().setStackInSlot(4, new ItemStack(CompanionAddons.TAG_FILTER.get()));
    long generation = a.generation(0, 0), revision = a.revision(0, 0);
    require(
        !a.edit(
            0,
            0,
            generation,
            revision,
            "deny",
            0,
            "42",
            ItemStack.EMPTY,
            h.getLevel().registryAccess()),
        "Typed boolean refuses numeric action");
    require(
        !a.edit(
            0,
            0,
            generation,
            revision,
            "missing",
            0,
            "true",
            ItemStack.EMPTY,
            h.getLevel().registryAccess()),
        "Undeclared control refused");
    require(
        a.edit(
            0,
            0,
            generation,
            revision,
            "deny",
            0,
            "true",
            ItemStack.EMPTY,
            h.getLevel().registryAccess()),
        "Declared typed action accepted");
    require(
        !a.edit(
            0,
            0,
            generation,
            a.revision(0, 0),
            "tag",
            0,
            "bad tag!",
            ItemStack.EMPTY,
            h.getLevel().registryAccess()),
        "Invalid tag refused atomically");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void refusedExecutionRetainsEscrowAcrossSave(GameTestHelper h) {
    var from = new net.neoforged.neoforge.items.ItemStackHandler(1);
    from.setStackInSlot(0, new ItemStack(Items.DIAMOND, 8));
    var to =
        new net.neoforged.neoforge.items.ItemStackHandler(1) {
          @Override
          public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return simulate ? ItemStack.EMPTY : stack;
          }
        };
    var machine = place(h);
    var escrow = machine.automation.transferState("input", 0);
    var transport = AutomationTransfers.transport(ResourceType.ITEMS).orElseThrow();
    require(
        transport.move(from, to, 8, value -> true, escrow, h.getLevel().registryAccess()) == 0,
        "No actual delivery");
    require(from.getStackInSlot(0).isEmpty(), "Extraction staged");
    var saved = machine.automation.save(h.getLevel().registryAccess());
    machine.automation.load(saved, h.getLevel().registryAccess());
    var accepting = new net.neoforged.neoforge.items.ItemStackHandler(1);
    escrow = machine.automation.transferState("input", 0);
    require(
        transport.move(from, accepting, 3, value -> true, escrow, h.getLevel().registryAccess())
            == 3,
        "Bounded retry");
    require(
        transport.move(from, accepting, 8, value -> true, escrow, h.getLevel().registryAccess())
            == 5,
        "Remaining escrow delivered");
    require(
        accepting.getStackInSlot(0).getCount() == 8 && !escrow.contains("item"),
        "No loss or duplication");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void pumpsAndRelaysRespectIndependentTanks(GameTestHelper h) {
    var machine = place(h);
    var neighbour = machine.getBlockPos().relative(Direction.NORTH);
    h.getLevel().setBlock(neighbour, CompanionMod.LOGISTICS.block.get().defaultBlockState(), 3);
    var source = (ProcessorEntity) h.getLevel().getBlockEntity(neighbour);
    source.energy.setStored(10_000);
    machine.energy.setStored(1000);
    source
        .fluids
        .tank(2)
        .setFluid(
            new net.neoforged.neoforge.fluids.FluidStack(
                net.minecraft.world.level.material.Fluids.WATER, 500));
    var a = machine.automation;
    a.slots()
        .setStackInSlot(
            AutomationInventory.addonSlot(2, 0), new ItemStack(CompanionAddons.INPUT_PUMP.get()));
    a.slots()
        .setStackInSlot(
            AutomationInventory.addonSlot(6, 0), new ItemStack(CompanionAddons.IMPORT_RELAY.get()));
    for (var f : RelativeFace.values()) a.group(3).sides().set(f, IoMode.DISABLED);
    var port = machine.fluids.view(i -> a.mode(ResourceType.FLUIDS, i, Direction.NORTH));
    require(port.getTanks() == 4, "All four tank numbers stay visible");
    h.runAfterDelay(
        35,
        () -> {
          require(
              machine.fluids.getFluidInTank(0).getAmount() == 500, "Input pump uses its own group");
          require(machine.fluids.getFluidInTank(1).isEmpty(), "Same-role tank is independent");
          require(source.fluids.getFluidInTank(2).isEmpty(), "Output tank drained");
          require(
              machine.energy.stored() + source.energy.stored() == 10_500,
              "Energy relay conserves energy; pump costs 500 RF");
          h.succeed();
        });
  }

  @GameTest(template = "empty")
  public static void compatibilityAndFilterLifetime(GameTestHelper h) {
    var machine = place(h);
    var a = machine.automation;
    require(a.groups().size() == 7, "Seven independent inventories");
    require(a.accepts(0, new ItemStack(CompanionAddons.PULLER.get())), "Input accepts puller");
    require(!a.accepts(0, new ItemStack(CompanionAddons.PUSHER.get())), "Input refuses pusher");
    require(!a.accepts(0, new ItemStack(CompanionAddons.INPUT_PUMP.get())), "Items refuse pump");
    require(!a.accepts(8, new ItemStack(CompanionAddons.PULLER.get())), "Output refuses puller");
    a.slots().setStackInSlot(0, new ItemStack(CompanionAddons.PULLER.get()));
    require(
        a.accepts(4, new ItemStack(CompanionAddons.ITEM_FILTER.get())),
        "Filter capability accepted");
    require(
        !a.accepts(4, new ItemStack(CompanionAddons.FLUID_FILTER.get())),
        "Wrong filter resource refused");
    a.slots().setStackInSlot(4, new ItemStack(CompanionAddons.ITEM_FILTER.get()));
    long gen = a.generation(0, 0), rev = a.revision(0, 0);
    require(
        a.edit(
            0,
            0,
            gen,
            rev,
            "samples",
            0,
            "sample",
            new ItemStack(Items.DIAMOND),
            h.getLevel().registryAccess()),
        "Sample edit");
    require(
        !a.edit(0, 0, gen, rev, "deny", 0, "", ItemStack.EMPTY, h.getLevel().registryAccess()),
        "Stale revision refused");
    var filter = a.filter(0, 0);
    var provider = filter.getCapability(AddonCapabilities.FILTER);
    require(
        provider.matches(filter, new ItemStack(Items.DIAMOND), h.getLevel().registryAccess()),
        "Configured predicate");
    require(
        !provider.matches(filter, new ItemStack(Items.DIRT), h.getLevel().registryAccess()),
        "Other item blocked");
    var saved = a.save(h.getLevel().registryAccess());
    a.load(saved, h.getLevel().registryAccess());
    a.load(saved, h.getLevel().registryAccess());
    require(a.filter(0, 0).getCount() == 1, "Reload does not duplicate filter");
    var player = h.makeMockServerPlayerInLevel();
    a.slots().extractItem(0, 1, false);
    a.recover(player);
    require(a.filter(0, 0).isEmpty(), "Parent extraction detaches child");
    require(
        player.getInventory().countItem(CompanionAddons.ITEM_FILTER.get()) == 1,
        "Filter returned exactly once");
    a.recover(player);
    require(
        player.getInventory().countItem(CompanionAddons.ITEM_FILTER.get()) == 1,
        "Recovery is idempotent");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void menuActionsAreSessionAndGenerationBound(GameTestHelper h) {
    var machine = place(h);
    var player = h.makeMockServerPlayerInLevel();
    player.setPos(
        machine.getBlockPos().getX(), machine.getBlockPos().getY(), machine.getBlockPos().getZ());
    var menu = new MachineMenu(71, player.getInventory(), machine);
    player.containerMenu = menu;
    var a = menu.automation;
    a.action(
        player,
        new AutomationNetwork.Action(
            71, menu.widgetSession() + 1, 1, "select", 0, -1, 0, 0, "", 0, ""));
    require(!a.open(), "Wrong session rejected");
    a.action(
        player,
        new AutomationNetwork.Action(
            71, menu.widgetSession(), 1, "select", 0, -1, 0, 0, "", 0, ""));
    require(
        a.open() && menu.slots.get(2).isActive() && !menu.slots.get(10).isActive(),
        "Only selected inventory active");
    a.action(
        player,
        new AutomationNetwork.Action(
            71, menu.widgetSession(), 2, "face", 0, -1, 0, 0, "OUTPUT", 0, ""));
    require(
        machine.automation.group(0).sides().get(RelativeFace.FRONT) == IoMode.INPUT,
        "Stale face refused");
    a.action(
        player,
        new AutomationNetwork.Action(
            71, menu.widgetSession(), 3, "face", 0, -1, 0, 0, "INPUT", 0, ""));
    require(
        machine.automation.group(0).sides().get(RelativeFace.FRONT) == IoMode.DISABLED,
        "Input cycles straight to disabled");
    a.action(
        player,
        new AutomationNetwork.Action(
            71, menu.widgetSession(), 3, "select", 1, -1, 0, 0, "", 0, ""));
    require(a.selected() == 0, "Replay refused");
    player.containerMenu = player.inventoryMenu;
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void outputAddonsTransferItemsFluidsAndEnergyThroughFilters(GameTestHelper h) {
    var machine = place(h);
    var pos = machine.getBlockPos().relative(Direction.NORTH);
    h.getLevel().setBlock(pos, CompanionMod.LOGISTICS.block.get().defaultBlockState(), 3);
    var target = (ProcessorEntity) h.getLevel().getBlockEntity(pos);
    machine.energy.setStored(10_000);
    // Leave room for one export batch without draining the source's powered item/fluid addons.
    target.energy.setStored(target.energy.capacity() - 1000);
    long totalEnergy = machine.energy.stored() + target.energy.stored();
    machine.items.setStackInSlot(1, new ItemStack(Items.DIAMOND, 16));
    machine
        .fluids
        .tank(2)
        .setFluid(
            new net.neoforged.neoforge.fluids.FluidStack(
                net.minecraft.world.level.material.Fluids.WATER, 500));
    var a = machine.automation;
    a.slots()
        .setStackInSlot(
            AutomationInventory.addonSlot(1, 0), new ItemStack(CompanionAddons.PUSHER.get()));
    a.slots()
        .setStackInSlot(
            AutomationInventory.filterSlot(1, 0),
            new ItemStack(CompanionAddons.LARGE_FILTER.get()));
    a.slots()
        .setStackInSlot(
            AutomationInventory.addonSlot(4, 0), new ItemStack(CompanionAddons.OUTPUT_PUMP.get()));
    a.slots()
        .setStackInSlot(
            AutomationInventory.filterSlot(4, 0),
            new ItemStack(CompanionAddons.FLUID_FILTER.get()));
    a.slots()
        .setStackInSlot(
            AutomationInventory.addonSlot(6, 0), new ItemStack(CompanionAddons.EXPORT_RELAY.get()));
    sample(h, a, 1, 35, new ItemStack(Items.DIRT));
    sample(h, a, 4, 0, new ItemStack(Items.LAVA_BUCKET));
    h.runAfterDelay(
        25,
        () -> {
          require(
              target.items.getStackInSlot(0).isEmpty() && target.fluids.getFluidInTank(0).isEmpty(),
              "Item and fluid filters block disallowed exports");
          require(
              machine.energy.stored() + target.energy.stored() == totalEnergy
                  && target.energy.stored() == target.energy.capacity(),
              "Export relay moves energy; refused item/fluid transfers spend no RF");
          sample(h, a, 1, 35, new ItemStack(Items.DIAMOND));
          sample(h, a, 4, 0, new ItemStack(Items.WATER_BUCKET));
        });
    h.runAfterDelay(
        70,
        () -> {
          require(
              machine.items.getStackInSlot(1).isEmpty()
                  && target.items.getStackInSlot(0).getCount() == 16,
              "Pusher exports every allowed item");
          require(
              machine.fluids.getFluidInTank(2).isEmpty()
                  && target.fluids.getFluidInTank(0).getAmount() == 500,
              "Output pump exports the allowed fluid");
          require(
              machine.energy.stored() + target.energy.stored() == totalEnergy - 564,
              "Only actual exports cost RF: 16 items at 4 RF and 500 mB at 1 RF");
          h.succeed();
        });
  }

  private static void sample(
      GameTestHelper h, AutomationInventory a, int group, int entry, ItemStack sample) {
    require(
        a.edit(
            group,
            0,
            a.generation(group, 0),
            a.revision(group, 0),
            "samples",
            entry,
            "sample",
            sample,
            h.getLevel().registryAccess()),
        "Filter sample accepted");
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void tagFilterControlsRealPulling(GameTestHelper h) {
    var machine = place(h);
    var pos = machine.getBlockPos().relative(Direction.NORTH);
    h.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
    var chest = (ChestBlockEntity) h.getLevel().getBlockEntity(pos);
    chest.setItem(0, new ItemStack(Items.DIRT, 8));
    chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 8));
    var a = machine.automation;
    a.slots().setStackInSlot(0, new ItemStack(CompanionAddons.PULLER.get()));
    a.slots().setStackInSlot(4, new ItemStack(CompanionAddons.TAG_FILTER.get()));
    require(
        a.edit(
            0,
            0,
            a.generation(0, 0),
            a.revision(0, 0),
            "tag",
            0,
            "minecraft:planks",
            ItemStack.EMPTY,
            h.getLevel().registryAccess()),
        "Tag filter configured");
    h.runAfterDelay(
        22,
        () -> {
          require(machine.items.getStackInSlot(0).isEmpty(), "Unpowered puller cannot transfer");
          machine.energy.setStored(1000);
        });
    h.runAfterDelay(
        65,
        () -> {
          require(
              machine.items.getStackInSlot(0).is(Items.OAK_PLANKS)
                  && machine.items.getStackInSlot(0).getCount() == 8,
              "Tag filter allows matching planks");
          require(
              chest.getItem(0).getCount() == 8 && chest.getItem(1).isEmpty(),
              "Tag filter leaves dirt behind");
          require(
              machine.energy.stored() == 968, "Powered tag-filtered transfer pays actual RF cost");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void poweredPullerHonoursFilterAndDisabledSides(GameTestHelper h) {
    var machine = place(h);
    var pos = machine.getBlockPos().relative(Direction.NORTH);
    h.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
    var chest = (ChestBlockEntity) h.getLevel().getBlockEntity(pos);
    chest.setItem(0, new ItemStack(Items.DIRT, 16));
    chest.setItem(1, new ItemStack(Items.DIAMOND, 16));
    machine.energy.setStored(1000);
    var a = machine.automation;
    a.slots().setStackInSlot(0, new ItemStack(CompanionAddons.PULLER.get()));
    a.slots().setStackInSlot(4, new ItemStack(CompanionAddons.ITEM_FILTER.get()));
    a.edit(
        0,
        0,
        a.generation(0, 0),
        a.revision(0, 0),
        "samples",
        0,
        "sample",
        new ItemStack(Items.DIAMOND),
        h.getLevel().registryAccess());
    for (var face : RelativeFace.values()) a.group(0).sides().set(face, IoMode.DISABLED);
    h.runAfterDelay(
        22,
        () -> {
          require(
              machine.items.getStackInSlot(0).isEmpty() && machine.energy.stored() == 1000,
              "Disabled sides idle without spending RF");
          a.group(0).sides().set(RelativeFace.FRONT, IoMode.INPUT);
        });
    h.runAfterDelay(
        65,
        () -> {
          require(
              machine.items.getStackInSlot(0).is(Items.DIAMOND)
                  && machine.items.getStackInSlot(0).getCount() == 16,
              "Allowed items pulled");
          require(
              chest.getItem(0).getCount() == 16 && chest.getItem(1).isEmpty(),
              "Rejected items remain");
          require(machine.energy.stored() == 936, "RF charged only for delivered items");
          h.succeed();
        });
  }
}
