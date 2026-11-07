package zone.moddev.mc.companion.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.companion.CompanionMod;
import zone.moddev.mc.companion.CreativePowerEntity;
import zone.moddev.mc.companion.ProcessorEntity;
import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.ScreenNetwork;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor.Status;

/** Exercises the consumer's registration and wiring with the real recipe files and block types. */
@GameTestHolder(CompanionMod.ID)
@PrefixGameTestTemplate(false)
public final class CompanionGameTests {
  private static final BlockPos CENTER = new BlockPos(2, 2, 2);

  @GameTest(template = "empty")
  public static void processorActivityTracksWorkAndHostGates(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.CRUSHER);
    var processor = machine.lanes.getFirst();
    require(!processor.paused() && !processor.processing(), "New processors are unpaused and idle");
    machine.items.setStackInSlot(0, new ItemStack(Items.COPPER_ORE, 16));
    machine.energy.setStored(100000);
    tick(machine, 2);
    require(processor.processing(), "Available work and energy start processing");
    machine.energy.setStored(0);
    machine.serverTick();
    require(
        !processor.processing() && !processor.paused(),
        "An energy stall is idle, not manually paused");
    machine.energy.setStored(100000);
    machine.serverTick();
    machine.setReservePercent(100);
    machine.serverTick();
    require(
        !processor.processing() && !processor.paused(),
        "An energy-reserve hold is not active processing");
    machine.setReservePercent(0);
    machine.serverTick();
    machine.redstone.mode(0, zone.moddev.mc.lib.api.redstone.RedstoneControl.Mode.READ);
    machine.redstone.range(0, 0, 15);
    machine.serverTick();
    require(
        !processor.processing() && !processor.paused() && machine.items.hasPlan(0),
        "Redstone holds work without manually pausing or releasing its reservation");
    processor.pause();
    require(processor.paused() && !processor.processing(), "Manual pause has its own state");
    processor.resume();
    machine.redstone.mode(0, zone.moddev.mc.lib.api.redstone.RedstoneControl.Mode.IGNORE);
    machine.serverTick();
    for (int ticks = 0; ticks < 200 && processor.progress() < processor.time() - 1; ticks++)
      machine.serverTick();
    require(processor.progress() == processor.time() - 1, "Resumed work reaches its final tick");
    machine.serverTick();
    require(
        processor.completedCycles().signum() > 0 && processor.processing(),
        "The completion tick remains processing so the icon does not flicker between cycles");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void genericProcessorButtonsRespectRedstoneAndLaneKeys(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.DUAL_CRUSHER);
    var player = helper.makeMockServerPlayerInLevel();
    var pos = machine.getBlockPos();
    player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
    var menu = new MachineMenu(42, player.getInventory(), machine);
    player.containerMenu = menu;
    var button =
        (zone.moddev.mc.lib.gui.model.Element.Button)
            menu.bound.screen().find("toggle_1").orElseThrow();
    require(
        button.processorKey().orElseThrow().equals("lane_1") && !button.common().clicks().any(),
        "The generic control targets a stable key without callbacks");
    int index = menu.index(button);
    menu.action(player, new ScreenNetwork.Action(42, index, 0, 1, 0, 0));
    require(!machine.lanes.get(1).paused(), "Right click cannot toggle the processor");
    menu.action(player, new ScreenNetwork.Action(42, index, 0, 0, 0, 1));
    require(!machine.lanes.get(1).paused(), "Invalid options cannot toggle the processor");
    var action = new ScreenNetwork.Action(42, index, 0, 0, 0, 0);
    menu.action(player, action);
    require(
        machine.lanes.get(1).paused() && !machine.lanes.getFirst().paused(),
        "Only the keyed lane pauses");
    require(
        !menu.redstone.watching() && !menu.redstone.conflicts(true),
        "No input watching means no conflict indicator");
    machine.redstone.mode(0, zone.moddev.mc.lib.api.redstone.RedstoneControl.Mode.EMIT);
    require(!menu.redstone.watching(), "Output-only redstone does not watch inputs");
    machine.redstone.mode(0, zone.moddev.mc.lib.api.redstone.RedstoneControl.Mode.READ);
    machine.redstone.range(0, 0, 15);
    machine.redstone.readInputs();
    require(
        menu.redstone.watching()
            && menu.redstone.paused()
            && !menu.redstone.conflicts(true)
            && menu.redstone.conflicts(false),
        "A redstone hold conflicts only with an enabled manual state");
    menu.action(player, action);
    require(
        !machine.lanes.get(1).paused() && menu.redstone.paused(),
        "Resume changes the manual state while redstone still holds processing");
    machine.redstone.outside(0, true);
    machine.redstone.readInputs();
    require(
        !menu.redstone.paused() && menu.redstone.conflicts(true) && !menu.redstone.conflicts(false),
        "Redstone allowing work conflicts with manual pause");

    int[] changes = {0}, syncs = {0};
    var host =
        new zone.moddev.mc.lib.machine.feature.FeatureHost(() -> changes[0]++, () -> syncs[0]++)
            .add(machine.lanes.get(1));
    require(
        host.setProcessorPaused("lane_1", true) && changes[0] == 1 && syncs[0] == 1,
        "Manual changes persist and sync without a processing tick");
    require(
        !host.setProcessorPaused("lane_1", true)
            && !host.setProcessorPaused("missing", true)
            && changes[0] == 1
            && syncs[0] == 1,
        "Repeated states and unknown keys are inert");
    machine.lanes.get(1).pause();
    machine.lanes.get(1).setPaused(false);
    require(!machine.lanes.get(1).paused(), "The public pause setter resumes too");
    var invalid =
        new zone.moddev.mc.lib.gui.model.Element.Button(
            button.common(),
            button.text(),
            button.icon(),
            button.shows(),
            button.icons(),
            button.valueLabels(),
            button.flat(),
            java.util.Optional.of("missing"));
    var original = menu.bound.screen();
    var invalidScreen =
        new zone.moddev.mc.lib.gui.model.Screen(
            original.id(),
            original.source(),
            original.title(),
            original.blockEntity(),
            original.playerInventory(),
            java.util.List.of(invalid),
            java.util.List.of());
    try {
      zone.moddev.mc.lib.gui.runtime.ScreenInventoryValidation.validate(invalidScreen, machine);
      throw new AssertionError("Unknown processor key accepted");
    } catch (zone.moddev.mc.lib.gui.load.ScreenException expected) {
      require(
          expected.getMessage().contains("Unknown processor-key: missing"),
          "An invalid control identifies its missing key");
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void heldProcessorsSaveSyncAndResumeWithoutReleasingReservations(
      GameTestHelper helper) {
    var machine = place(helper, CompanionMod.CRUSHER);
    var lane = machine.lanes.getFirst();
    machine.items.setStackInSlot(0, new ItemStack(Items.COPPER_ORE, 4));
    machine.energy.setStored(100000);
    machine.serverTick();
    machine.serverTick();
    int progress = lane.progress();
    var plan = machine.items.plan(0);
    var changes = new java.util.concurrent.atomic.AtomicInteger();
    var syncs = new java.util.concurrent.atomic.AtomicInteger();
    var host =
        new zone.moddev.mc.lib.machine.feature.FeatureHost(
                changes::incrementAndGet, syncs::incrementAndGet)
            .add(lane);
    host.tick(helper.getLevel(), false);
    require(lane.status() == Status.HELD && !lane.paused(), "Host hold differs from manual pause");
    require(changes.get() == 1 && syncs.get() == 1, "Entering hold saves and synchronizes status");
    require(
        lane.state().status().orElseThrow() == Status.HELD
            && lane.clientState().status().orElseThrow() == Status.HELD,
        "Save and client snapshots show hold");
    require(
        lane.progress() == progress && machine.items.plan(0).equals(plan),
        "Hold retains progress and reservations");
    for (int i = 0; i < 20; i++) host.tick(helper.getLevel(), false);
    require(
        changes.get() == 1 && syncs.get() == 1, "Unchanged hold does not repeatedly save or sync");
    host.tick(helper.getLevel(), true);
    require(
        lane.status() == Status.RUNNING && lane.progress() == progress + 1 && syncs.get() == 2,
        "Releasing hold resumes and synchronizes the running state");
    machine.setReservePercent(100);
    machine.serverTick();
    require(lane.status() == Status.HELD, "Energy reserve gate reports held");
    machine.setReservePercent(0);
    machine.redstone.mode(0, zone.moddev.mc.lib.api.redstone.RedstoneControl.Mode.READ);
    machine.redstone.range(0, 0, 15);
    machine.redstone.outside(0, false);
    machine.serverTick();
    require(lane.status() == Status.HELD, "Redstone gate reports held");
    lane.interrupt();
    host.tick(helper.getLevel(), false);
    require(
        lane.status() == Status.PAUSED && machine.items.plan(0).take() == 0,
        "Manual pause remains visible and releases reservations even during a host hold");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void statisticsProvidersDescribeCurrentRecipes(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.DUAL_CRUSHER);
    var first = machine.lanes.getFirst();
    require(
        machine.screenStats().size() == 1 + machine.lanes.size(),
        "Only energy and processors contribute statistics");
    require(
        first.statistics().rows().get(1).value()
            instanceof zone.moddev.mc.lib.api.stats.Statistics.Text,
        "Idle processor maximum explicitly has no recipe");
    machine.items.setStackInSlot(0, new ItemStack(Items.COPPER_ORE, 16));
    machine.energy.setStored(100000);
    machine.serverTick();
    long peak = 0;
    for (var lane : machine.lanes) {
      var section = lane.statistics();
      require(
          section.title().equals(lane.displayName()) && section.rows().size() == 2,
          "Processor provides a named section with exactly two rows");
      var maximum = (zone.moddev.mc.lib.api.stats.Statistics.Rate) section.rows().get(1).value();
      require(
          maximum.amount().equals(java.math.BigInteger.ONE) && maximum.ticks() == 100,
          "Maximum throughput uses the current copper recipe's duration");
      require(
          section.maximumUses().size() == 1
              && section.maximumUses().getFirst().resource() == machine.energy,
          "Demand is attributed to the backing buffer, not the group's wrapper");
      peak += section.maximumUses().getFirst().perTick().longValueExact();
    }
    require(peak == 40, "Two current processors contribute 20 RF/t each");
    machine.serverTick();
    require(
        machine.energy.used().equals(java.math.BigInteger.valueOf(40))
            && machine.energy.extracted().signum() == 0,
        "Recipe consumption is used RF, not output RF");
    first.reset();
    require(first.statistics().maximumUses().isEmpty(), "Reset removes stale recipe demand");
    require(
        first.statistics().rows().get(1).value()
            instanceof zone.moddev.mc.lib.api.stats.Statistics.Text,
        "Reset removes stale maximum cycle rate");
    helper.getLevel().setBlock(machine.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
    helper.succeed();
  }

  private static <T> ScreenNetwork.Widget widgetPacket(
      MachineMenu menu,
      String element,
      int request,
      zone.moddev.mc.lib.gui.WidgetAction<T> action,
      T value) {
    return new ScreenNetwork.Widget(
        menu.containerId,
        menu.widgetSession(),
        menu.index(menu.bound.screen().find(element).orElseThrow()),
        request,
        action.id(),
        action.encode(menu.blockEntity.getLevel().registryAccess(), value));
  }

  @GameTest(template = "empty")
  public static void typedWidgetActionsAuthorizeBeforeMutation(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.CRUSHER);
    var player = helper.makeMockServerPlayerInLevel();
    var pos = machine.getBlockPos();
    player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    var menu = new MachineMenu(51, player.getInventory(), machine);
    player.containerMenu = menu;
    var rename = zone.moddev.mc.companion.CompanionWidgets.RENAME;
    try {
      require(
          !menu.widgetAction(player, widgetPacket(menu, "name_editor", 1, rename, "Hidden"))
              .accepted(),
          "Hidden widgets cannot mutate the machine");
      menu.navigate("panel", "settings");
      var packet = widgetPacket(menu, "name_editor", 2, rename, "Copper crusher");
      require(
          menu.widgetAction(
                  player,
                  new ScreenNetwork.Widget(
                      51,
                      menu.widgetSession() + 1,
                      packet.element(),
                      2,
                      rename.id(),
                      packet.data()))
              == null,
          "Old menu sessions are rejected");
      var outsider = helper.makeMockServerPlayerInLevel();
      require(menu.widgetAction(outsider, packet) == null, "Other players cannot use this menu");
      var accepted = menu.widgetAction(player, packet);
      require(
          accepted.accepted()
              && ((net.minecraft.network.chat.Component) accepted.value())
                  .getString()
                  .equals("Copper crusher"),
          "Accept response carries the value after the handler, in the same tick");
      require(menu.widgetAction(player, packet) == null, "Requests cannot be replayed");
      var rejected = menu.widgetAction(player, widgetPacket(menu, "name_editor", 3, rename, "   "));
      require(
          !rejected.accepted() && machine.getMachineName().getString().equals("Copper crusher"),
          "Invalid text is rejected without changing the authoritative name");
      var data = rename.encode(player.registryAccess(), "Malformed");
      require(
          !menu.widgetAction(
                  player,
                  new ScreenNetwork.Widget(
                      51,
                      menu.widgetSession(),
                      packet.element(),
                      4,
                      rename.id(),
                      java.util.Arrays.copyOf(data, data.length + 1)))
              .accepted(),
          "Trailing data is rejected");
      require(
          !menu.widgetAction(
                  player,
                  widgetPacket(
                      menu,
                      "name_editor",
                      5,
                      zone.moddev.mc.companion.CompanionWidgets.RESERVE,
                      50))
              .accepted(),
          "Actions are bound to widget types");
      player.setPos(pos.getX() + 100, pos.getY(), pos.getZ());
      require(
          menu.widgetAction(player, widgetPacket(menu, "name_editor", 6, rename, "Too far"))
              == null,
          "Distance is checked before decoding");
      var saved = machine.saveWithFullMetadata(helper.getLevel().registryAccess());
      var copy =
          (ProcessorEntity)
              BlockEntity.loadStatic(
                  pos, machine.getBlockState(), saved, helper.getLevel().registryAccess());
      require(copy.getMachineName().getString().equals("Copper crusher"), "Name persists");
    } finally {
      player.containerMenu = player.inventoryMenu;
      helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void structuredWidgetActionsBoundValuesRateAndPendingEdits(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.CRUSHER);
    var player = helper.makeMockServerPlayerInLevel();
    var pos = machine.getBlockPos();
    player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    var menu = new MachineMenu(52, player.getInventory(), machine);
    player.containerMenu = menu;
    menu.navigate("panel", "settings");
    var reserve = zone.moddev.mc.companion.CompanionWidgets.RESERVE;
    var lane = zone.moddev.mc.companion.CompanionWidgets.SET_LANE;
    try {
      require(
          !menu.widgetAction(player, widgetPacket(menu, "reserve_slider", 1, reserve, 101))
              .accepted(),
          "Reserve range checked");
      require(
          menu.widgetAction(player, widgetPacket(menu, "reserve_slider", 2, reserve, 25))
              .accepted(),
          "Valid reserve accepted");
      require(
          !menu.widgetAction(
                  player,
                  widgetPacket(
                      menu,
                      "lane_selector",
                      3,
                      lane,
                      new zone.moddev.mc.companion.CompanionWidgets.LaneSetting(-1, true)))
              .accepted(),
          "Negative lane rejected");
      require(
          !menu.widgetAction(
                  player,
                  widgetPacket(
                      menu,
                      "lane_selector",
                      4,
                      lane,
                      new zone.moddev.mc.companion.CompanionWidgets.LaneSetting(1, true)))
              .accepted(),
          "Unconfigured lane rejected");
      require(
          menu.widgetAction(
                      player,
                      widgetPacket(
                          menu,
                          "lane_selector",
                          5,
                          lane,
                          new zone.moddev.mc.companion.CompanionWidgets.LaneSetting(0, true)))
                  .accepted()
              && machine.getPaused(),
          "Structured pause accepted");
      require(
          menu.widgetAction(
                      player,
                      widgetPacket(
                          menu,
                          "lane_selector",
                          6,
                          lane,
                          new zone.moddev.mc.companion.CompanionWidgets.LaneSetting(0, false)))
                  .accepted()
              && !machine.getPaused(),
          "Structured resume accepted");
      menu.widgetAction(player, widgetPacket(menu, "reserve_slider", 7, reserve, 25));
      menu.widgetAction(player, widgetPacket(menu, "reserve_slider", 8, reserve, 25));
      var excess = menu.widgetAction(player, widgetPacket(menu, "reserve_slider", 9, reserve, 26));
      require(
          excess != null && !excess.accepted() && ((Number) excess.value()).intValue() == 25,
          "Excess edits are immediately rejected with the last sent value");
      require(machine.getReservePercent() == 25, "Rate-limited edits do not run their handler");
      var saved = machine.saveWithFullMetadata(helper.getLevel().registryAccess());
      var copy =
          (ProcessorEntity)
              BlockEntity.loadStatic(
                  pos, machine.getBlockState(), saved, helper.getLevel().registryAccess());
      require(copy.getReservePercent() == 25, "Reserve persists");
      var name =
          (zone.moddev.mc.lib.gui.model.Element.Custom)
              menu.bound.screen().find("name_editor").orElseThrow();
      require(
          menu.prepareWidgetAction(
                  name, zone.moddev.mc.companion.CompanionWidgets.RENAME, "x".repeat(33))
              .isEmpty(),
          "Codec bounds failures are refused without escaping into the client");
      require(
          menu.prepareWidgetAction(name, zone.moddev.mc.companion.CompanionWidgets.RENAME, null)
              .isEmpty(),
          "Other codec runtime failures are also refused");
      require(
          !menu.widgetPending(name) && menu.takeWidgetResult(name) == null,
          "Failed encoding leaves the widget ready for another edit");
      var pending =
          menu.prepareWidgetAction(
                  name, zone.moddev.mc.companion.CompanionWidgets.RENAME, "Pending")
              .orElseThrow();
      require(pending.request() == 1, "Failed encoding does not consume request numbers");
      require(
          menu.prepareWidgetAction(name, zone.moddev.mc.companion.CompanionWidgets.RENAME, "Second")
              .isEmpty(),
          "Only one in-flight edit per widget");
      menu.receiveWidgetResult(
          new ScreenNetwork.WidgetResult(
              52,
              menu.widgetSession() + 1,
              pending.element(),
              pending.request(),
              true,
              net.minecraft.network.chat.Component.literal("Stale"),
              null));
      require(menu.widgetPending(name), "A stale response cannot settle a current edit");
      for (int i = 0; i < 100; i++) menu.tickWidgetActions();
      require(
          !menu.widgetPending(name) && !menu.takeWidgetResult(name).accepted(),
          "Timed-out edits unlock for retry");
    } finally {
      player.containerMenu = player.inventoryMenu;
      helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void widgetRejectionUnlocksImmediatelyAndAcknowledgmentsTrackDeltas(
      GameTestHelper helper) {
    var machine = place(helper, CompanionMod.CRUSHER);
    var player = helper.makeMockServerPlayerInLevel();
    var pos = machine.getBlockPos();
    player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    var server = new MachineMenu(54, player.getInventory(), machine);
    var client = new MachineMenu(54, player.getInventory(), machine, server.widgetSession());
    player.containerMenu = server;
    server.navigate("panel", "settings");
    client.navigate("panel", "settings");
    var name =
        (zone.moddev.mc.lib.gui.model.Element.Custom)
            server.bound.screen().find("name_editor").orElseThrow();
    int key = server.index(name) * 2;
    var rename = zone.moddev.mc.companion.CompanionWidgets.RENAME;
    var original = machine.getMachineName();
    server.apply(java.util.Map.of(key, original));
    client.apply(java.util.Map.of(key, original));
    try {
      var packet = client.prepareWidgetAction(name, rename, "Temporary").orElseThrow();
      var result = server.widgetAction(player, packet);
      client.receiveWidgetResult(result);
      require(client.takeWidgetResult(name).accepted(), "First edit accepted");
      // The machine changes back before a regular broadcast has observed Temporary.
      machine.rename(original.getString());
      var delta =
          zone.moddev.mc.lib.gui.runtime.ScreenValues.changed(
              java.util.Map.of(key, server.wireValue(key)), java.util.Map.of(key, original), false);
      require(delta.containsKey(key), "Reversion must be sent after an acknowledgment");
      client.apply(delta);
      server.apply(delta);
      require(original.equals(client.value(name, false)), "Client converges to the reverted value");
      for (int i = 2; i <= 9; i++) {
        packet = client.prepareWidgetAction(name, rename, "Edit " + i).orElseThrow();
        result = server.widgetAction(player, packet);
        require(result != null && result.accepted() == (i <= 8), "Only eight edits may dispatch");
        client.receiveWidgetResult(result);
        require(!client.widgetPending(name), "Rejection unlocks without waiting for timeout");
        client.takeWidgetResult(name);
      }
      require(machine.getMachineName().getString().equals("Edit 8"), "Ninth edit never mutates");
      require(server.widgetAction(player, packet) == null, "Rejected requests cannot be replayed");
    } finally {
      player.containerMenu = player.inventoryMenu;
      helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void clearedProcessorsRestartWithoutManualResume(GameTestHelper helper) {
    for (var type :
        java.util.List.of(
            CompanionMod.CRUSHER, CompanionMod.TRANSMUTER, CompanionMod.DUAL_CRUSHER)) {
      var machine = place(helper, type);
      var input = type == CompanionMod.TRANSMUTER ? Items.IRON_INGOT : Items.COPPER_ORE;
      machine.items.setStackInSlot(0, new ItemStack(input));
      machine.energy.setStored(100_000);
      tick(machine, 6);
      machine.clearContent();
      for (var lane : machine.lanes)
        require(
            !lane.paused() && lane.progress() == 0 && lane.state().resumeRecipe().isEmpty(),
            "Clearing resets pause, progress and remembered recipe");
      for (int slot = 0; slot < machine.items.getSlots(); slot++)
        require(
            machine.items.getStackInSlot(slot).isEmpty() && !machine.items.hasPlan(slot),
            "Clearing empties inventory and reservations");
      machine.items.setStackInSlot(0, new ItemStack(input));
      machine.energy.setStored(100_000);
      tick(machine, 202);
      int output = type == CompanionMod.TRANSMUTER ? 0 : 1;
      require(
          machine
              .items
              .getStackInSlot(output)
              .is(type == CompanionMod.TRANSMUTER ? Items.GOLD_INGOT : Items.RAW_COPPER),
          "Cleared machine processes fresh input without manual resume");
      machine.lanes.forEach(lane -> lane.interrupt());
      machine.clearContent();
      require(
          machine.lanes.stream().noneMatch(lane -> lane.paused()),
          "Clearing also resets a paused machine");
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void registeredScreensAndRecipes(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(CENTER);
    var player = helper.makeMockServerPlayerInLevel();
    for (var machine : CompanionMod.MACHINES) {
      var id = BuiltInRegistries.BLOCK.getKey(machine.block.get());
      require(Screens.get(id).isPresent(), "Missing screen " + id);
      level.setBlock(pos, machine.block.get().defaultBlockState(), 3);
      var entity = level.getBlockEntity(pos);
      require(entity.getType() == machine.type.get(), "Wrong block entity for " + id);
      var menu = new MachineMenu(41, player.getInventory(), entity);
      require(menu.bound.screen().id().equals(id), "Wrong menu for " + id);
      level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }
    require(
        CompanionMod.CRUSHING.recipes(level.getRecipeManager()).size() == 5,
        "Five crusher recipes");
    require(
        CompanionMod.TRANSMUTING.recipes(level.getRecipeManager()).size() == 1,
        "One converter recipe");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void customWidgetUsesValidatedMenuActions(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.CRUSHER);
    var player = helper.makeMockServerPlayerInLevel();
    var pos = machine.getBlockPos();
    player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    var menu = new MachineMenu(47, player.getInventory(), machine);
    player.containerMenu = menu;
    try {
      var dial = menu.bound.screen().find("progress").orElseThrow();
      require(
          dial instanceof zone.moddev.mc.lib.gui.model.Element.Custom,
          "The consuming mod's widget loads on a dedicated server");
      int index = menu.index(dial);
      menu.action(player, new ScreenNetwork.Action(47, index, 0, 0, 0, 1));
      require(!machine.getPaused(), "Custom widgets reject unrecognized action payloads");
      menu.action(player, new ScreenNetwork.Action(48, index, 0, 0, 0, 0));
      require(!machine.getPaused(), "A stale menu id cannot act on a custom widget");
      menu.action(player, new ScreenNetwork.Action(47, index, 0, 0, 0, 0));
      require(machine.getPaused(), "Custom widget click invokes the registered server handler");
      menu.action(player, new ScreenNetwork.Action(47, index, 0, 0, 0, 0));
      require(!machine.getPaused(), "A second custom widget click resumes the machine");
    } finally {
      // Mock players have no client connection to receive subsequent menu ticks.
      player.containerMenu = player.inventoryMenu;
      helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void redstoneControlsHoldProgressAndRespectManualPause(GameTestHelper helper) {
    RedstoneSmoke.check(helper);
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void shiftClickMergeMarksIdleMachineForSave(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.CRUSHER);
    var level = helper.getLevel();
    var chunk = level.getChunkAt(machine.getBlockPos());
    var player = helper.makeMockServerPlayerInLevel();
    var inventory = player.getInventory();
    machine.items.setStackInSlot(0, new ItemStack(Items.COPPER_ORE, 60));
    // Keep the newly fillable outputs full so this regression isolates an input merge.
    machine.items.setStackInSlot(1, new ItemStack(Items.RAW_COPPER, 64));
    machine.items.setStackInSlot(2, new ItemStack(Items.RAW_COPPER, 64));
    inventory.setItem(9, new ItemStack(Items.COPPER_ORE, 8));
    var menu = new MachineMenu(44, inventory, machine);
    int source =
        menu.slots.stream()
            .filter(slot -> slot.container == inventory && slot.getContainerSlot() == 9)
            .findFirst()
            .orElseThrow()
            .index;
    require(
        !machine.items.hasPlan(0) && machine.energy.stored() == 0,
        "Regression starts without reservations or power to trigger unrelated saves");
    try {
      // Model an already-saved chunk; no server ticks run between this and the assertion.
      chunk.setUnsaved(false);
      require(!chunk.isUnsaved(), "Chunk starts clean");
      require(
          !menu.quickMoveStack(player, source).isEmpty(), "Shift-click merges into existing input");
      require(
          machine.items.getStackInSlot(0).getCount() == 64 && inventory.getItem(9).getCount() == 4,
          "Merge respects capacity and retains the remainder");
      require(chunk.isUnsaved(), "Shift-click merge must mark the idle machine chunk for saving");
      var saved = machine.saveWithFullMetadata(level.registryAccess());
      var copy =
          (ProcessorEntity)
              BlockEntity.loadStatic(
                  machine.getBlockPos(), machine.getBlockState(), saved, level.registryAccess());
      require(copy.items.getStackInSlot(0).getCount() == 64, "Merged stack survives save/load");

      chunk.setUnsaved(false);
      require(
          menu.quickMoveStack(player, source).isEmpty(), "Full input rejects another shift-click");
      require(!chunk.isUnsaved(), "Rejected shift-click does not dirty the machine chunk");
    } finally {
      chunk.setUnsaved(true);
      level.setBlock(machine.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void partialShiftClickExtractionMarksMachineForSave(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.CRUSHER);
    var level = helper.getLevel();
    var chunk = level.getChunkAt(machine.getBlockPos());
    var player = helper.makeMockServerPlayerInLevel();
    var inventory = player.getInventory();
    for (int slot = 0; slot < 36; slot++)
      inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
    inventory.setItem(9, new ItemStack(Items.RAW_COPPER, 60));
    machine.items.setStackInSlot(1, new ItemStack(Items.RAW_COPPER, 8));
    var menu = new MachineMenu(45, inventory, machine);
    try {
      chunk.setUnsaved(false);
      require(!chunk.isUnsaved(), "Chunk starts clean before partial extraction");
      require(!menu.quickMoveStack(player, 1).isEmpty(), "Shift-click tops up the player stack");
      require(
          machine.items.getStackInSlot(1).getCount() == 4 && inventory.getItem(9).getCount() == 64,
          "Partial extraction retains four machine items");
      require(
          chunk.isUnsaved(),
          "Partial shift-click extraction must mark the machine chunk for saving");
      var saved = machine.saveWithFullMetadata(level.registryAccess());
      var copy =
          (ProcessorEntity)
              BlockEntity.loadStatic(
                  machine.getBlockPos(), machine.getBlockState(), saved, level.registryAccess());
      require(
          copy.items.getStackInSlot(1).getCount() == 4,
          "Only the remaining output survives save/load");
    } finally {
      chunk.setUnsaved(true);
      level.setBlock(machine.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void placementFacingKeepsNeighboursAndPortsOnTheSameSide(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(CENTER);
    var player = helper.makeMockServerPlayerInLevel();
    var leftFace = zone.moddev.mc.lib.api.capabilities.RelativeFace.LEFT;
    var disabled = zone.moddev.mc.lib.api.capabilities.IoMode.DISABLED;
    var output = zone.moddev.mc.lib.api.capabilities.IoMode.OUTPUT;
    for (var look : Direction.Plane.HORIZONTAL) {
      player.setYRot(look.toYRot());
      var left = look.getCounterClockWise();
      for (var machine : CompanionMod.MACHINES) {
        var context =
            new net.minecraft.world.item.context.BlockPlaceContext(
                player,
                InteractionHand.MAIN_HAND,
                new ItemStack(machine.block.get()),
                new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false));
        var state = machine.block.get().getStateForPlacement(context);
        require(
            state.getValue(zone.moddev.mc.companion.DemoBlock.FACING) == look.getOpposite(),
            "Placed machine faces its player: " + look);
        level.setBlock(pos, state, 3);
        var entity = (zone.moddev.mc.companion.DemoEntity) level.getBlockEntity(pos);
        var host = (zone.moddev.mc.lib.gui.runtime.ScreenHost) entity;
        for (var group : host.screenAutomation().groups())
          require(
              group.world(leftFace) == left, "Every resource group uses placement-relative left");
        if (entity instanceof ProcessorEntity processor)
          require(
              processor.redstone.world(leftFace.ordinal()) == left,
              "Redstone and resource drawers agree on left");
        var rotated = state.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
        require(
            rotated.getValue(zone.moddev.mc.companion.DemoBlock.FACING)
                == look.getOpposite().getClockWise(),
            "Rotation changes the stored facing");
        var mirrored = state.mirror(net.minecraft.world.level.block.Mirror.LEFT_RIGHT);
        var expectedMirror = look.getAxis() == Direction.Axis.Z ? look : look.getOpposite();
        require(
            mirrored.getValue(zone.moddev.mc.companion.DemoBlock.FACING) == expectedMirror,
            "Mirror changes the stored facing");
        if (entity instanceof CreativePowerEntity power) {
          level.setBlock(
              pos.relative(left),
              CompanionMod.CRUSHER.block.get().getStateForPlacement(context),
              3);
          var receiver = (ProcessorEntity) level.getBlockEntity(pos.relative(left));
          require(
              receiver.facing() == power.facing(),
              "Both placements from one position share a facing");
          var group = power.automation.group(0);
          for (var face : zone.moddev.mc.lib.api.capabilities.RelativeFace.values())
            group.sides().set(face, disabled);
          group.sides().set(leftFace, output);
          var port = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, left);
          require(port.canExtract(), "Left capability is enabled");
          require(
              !level
                  .getCapability(Capabilities.EnergyStorage.BLOCK, pos, left.getOpposite())
                  .canExtract(),
              "Right capability stays disabled");
          power.serverTick();
          require(receiver.energy.stored() == 1000, "Left output powers the left neighbour");
          group.sides().set(leftFace, disabled);
          power.serverTick();
          require(
              receiver.energy.stored() == 1000 && !port.canExtract(),
              "Disabling left blocks the neighbour and the cached capability");
          group.sides().set(leftFace, output);
          level.setBlock(pos, rotated, 3);
          require(
              level.getBlockEntity(pos) == power && group.world(leftFace) == left.getClockWise(),
              "Existing group follows block rotation");
          require(!port.canExtract(), "Cached world-side capability follows rotation");
          level.setBlock(pos.relative(left), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
      }
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void creativePowerSidesGatePushAndPullAndPersist(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(CENTER);
    level.setBlock(pos, CompanionMod.CREATIVE_POWER.block.get().defaultBlockState(), 3);
    level.setBlock(pos.above(), CompanionMod.CRUSHER.block.get().defaultBlockState(), 3);
    var power = (CreativePowerEntity) level.getBlockEntity(pos);
    var receiver = (ProcessorEntity) level.getBlockEntity(pos.above());
    var port = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, Direction.UP);
    var group = power.automation.group(0);
    var top = zone.moddev.mc.lib.api.capabilities.RelativeFace.TOP;
    var output = zone.moddev.mc.lib.api.capabilities.IoMode.OUTPUT;
    var disabled = zone.moddev.mc.lib.api.capabilities.IoMode.DISABLED;
    require(power.screenAutomation() == power.automation, "Generator exposes side drawer");
    var menu = new MachineMenu(46, helper.makeMockServerPlayerInLevel().getInventory(), power);
    require(
        menu.slots.isEmpty()
            && menu.automation.slotCount() == 0
            && !menu.automation.addonsEnabled(),
        "Generator side configuration exposes no inventory slots");
    require(menu.bound.screen().find("info").isPresent(), "Generator has an Info panel");
    require(group.cycle(top, output, false), "Output side can be disabled");
    require(
        !port.canExtract() && port.extractEnergy(1000, false) == 0,
        "Existing capability follows disabled side");
    power.serverTick();
    require(receiver.energy.stored() == 0, "Disabled side does not push energy");
    var saved = power.saveWithFullMetadata(level.registryAccess());
    var copy =
        (CreativePowerEntity)
            BlockEntity.loadStatic(pos, power.getBlockState(), saved, level.registryAccess());
    require(copy.automation.group(0).sides().get(top) == disabled, "Disabled side survives reload");
    saved.remove("automation");
    var legacy =
        (CreativePowerEntity)
            BlockEntity.loadStatic(pos, power.getBlockState(), saved, level.registryAccess());
    require(
        legacy.automation.group(0).sides().get(top) == output,
        "Older generators retain all outputs");
    require(group.cycle(top, disabled, false), "Disabled side cycles back to output");
    require(port.canExtract() && !port.canReceive(), "Generator remains output-only");
    power.serverTick();
    require(receiver.energy.stored() == 1000, "Re-enabled side pushes energy");
    level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void creativePowerFeedsAllFaces(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(CENTER);
    level.setBlock(pos, CompanionMod.CREATIVE_POWER.block.get().defaultBlockState(), 3);
    var power = (CreativePowerEntity) level.getBlockEntity(pos);
    for (var side : Direction.values())
      level.setBlock(pos.relative(side), CompanionMod.CRUSHER.block.get().defaultBlockState(), 3);
    power.serverTick();
    for (var side : Direction.values()) {
      var machine = (ProcessorEntity) level.getBlockEntity(pos.relative(side));
      require(machine.energy.stored() == 1_000, "Power reaches " + side);
      var view =
          level.getCapability(
              Capabilities.EnergyStorage.BLOCK, pos.relative(side), side.getOpposite());
      require(view.canReceive() && !view.canExtract(), "Processor energy is input-only");
      var unsided = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(side), null);
      require(
          unsided != null
              && !unsided.canExtract()
              && unsided.extractEnergy(1_000, true) == 0
              && unsided.extractEnergy(1_000, false) == 0
              && machine.energy.stored() == 1_000,
          "Unsided Crusher access cannot drain energy");
      level.setBlock(pos.relative(side), Blocks.AIR.defaultBlockState(), 3);
    }
    var output = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, Direction.UP);
    require(output.canExtract() && !output.canReceive(), "Source energy is output-only");
    require(output.extractEnergy(2_000, false) == 1_000, "Source limits extraction per call");
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void crusherUsesAdjacentPower(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(CENTER);
    var crusher = place(helper, CompanionMod.CRUSHER);
    level.setBlock(pos.below(), CompanionMod.CREATIVE_POWER.block.get().defaultBlockState(), 3);
    var source = (CreativePowerEntity) level.getBlockEntity(pos.below());
    var input = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
    require(input.insertItem(0, new ItemStack(Items.COPPER_ORE), false).isEmpty(), "Accepts ore");
    require(
        input.insertItem(1, new ItemStack(Items.RAW_COPPER), true).isEmpty(),
        "Crusher output accepts insertion for testing");
    for (int i = 0; i <= 100; i++) {
      source.serverTick();
      crusher.serverTick();
    }
    require(crusher.items.getStackInSlot(0).isEmpty(), "Consumed one ore");
    require(
        crusher.items.getStackInSlot(1).is(Items.RAW_COPPER)
            && crusher.items.getStackInSlot(1).getCount() == 2,
        "Produced two raw copper");
    require(input.extractItem(1, 64, false).getCount() == 2, "Automation extracts output");
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void crusherSplitsOutputAcrossNearlyFullSlots(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.CRUSHER);
    var level = helper.getLevel();
    var pos = machine.getBlockPos();
    require(machine.items.getSlots() == 3, "Crusher has one input and two outputs");
    require(
        machine.automation.group(1).members().equals(java.util.List.of(1, 2)),
        "Both outputs belong to one configurable inventory");
    var player = helper.makeMockServerPlayerInLevel();
    player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
    var menu = new MachineMenu(88, player.getInventory(), machine);
    player.containerMenu = menu;
    for (int slot : new int[] {1, 2}) {
      menu.setCarried(new ItemStack(Items.DIRT, 7));
      menu.clicked(slot, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
      require(
          machine.items.getStackInSlot(slot).is(Items.DIRT)
              && machine.items.getStackInSlot(slot).getCount() == 7
              && menu.getCarried().isEmpty(),
          "Players can fill either crusher output with arbitrary items");
      menu.clicked(slot, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
      require(
          menu.getCarried().getCount() == 7 && machine.items.getStackInSlot(slot).isEmpty(),
          "Players can take their test items back");
      menu.setCarried(ItemStack.EMPTY);
    }
    player.containerMenu = player.inventoryMenu;
    machine.items.insertItem(0, new ItemStack(Items.IRON_ORE), false);
    var fill = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
    require(
        fill.insertItem(1, new ItemStack(Items.RAW_IRON, 63), false).isEmpty()
            && fill.insertItem(2, new ItemStack(Items.RAW_IRON, 63), false).isEmpty(),
        "Both output slots accept external filling before the recipe starts");
    machine.energy.setStored(100000);
    tick(machine, 1);
    require(
        machine.items.plan(0).take() == 1
            && machine.items.plan(1).add().is(Items.RAW_IRON)
            && machine.items.plan(1).add().getCount() == 1
            && machine.items.plan(2).add().is(Items.RAW_IRON)
            && machine.items.plan(2).add().getCount() == 1,
        "Two raw iron reserve one space in each stack of 63");
    require(
        machine.items.insertResult(1, new ItemStack(Items.RAW_IRON), true).getCount() == 1
            && machine.items.insertResult(2, new ItemStack(Items.RAW_IRON), true).getCount() == 1,
        "Both last spaces are protected by reservations");
    var saved = machine.saveWithFullMetadata(level.registryAccess());
    var copy =
        (ProcessorEntity)
            BlockEntity.loadStatic(pos, machine.getBlockState(), saved, level.registryAccess());
    copy.setLevel(level);
    require(
        copy.items.plan(1).add().getCount() == 1 && copy.items.plan(2).add().getCount() == 1,
        "Split reservations survive saving and loading");
    tick(copy, 100);
    require(
        copy.items.getStackInSlot(0).isEmpty()
            && copy.items.getStackInSlot(1).getCount() == 64
            && copy.items.getStackInSlot(2).getCount() == 64
            && !copy.items.hasPlan(1)
            && !copy.items.hasPlan(2),
        "Completion fills both stacks to 64 and releases their plans");
    var capability = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
    require(
        capability.getSlots() == 3
            && capability.insertItem(2, new ItemStack(Items.RAW_IRON), true).getCount() == 1
            && capability.extractItem(2, 1, true).getCount() == 1,
        "Second output permits extraction but protects its reserved last space");
    machine.lanes.forEach(lane -> lane.reset());
    machine.items.setStackInSlot(1, new ItemStack(Items.RAW_IRON, 63));
    machine.items.setStackInSlot(2, new ItemStack(Items.RAW_IRON, 64));
    long energy = machine.energy.stored();
    tick(machine, 1);
    require(
        machine.getTime() == 0
            && machine.items.plan(0).isNone()
            && machine.items.plan(1).isNone()
            && machine.items.plan(2).isNone()
            && machine.energy.stored() == energy,
        "Only one free space refuses the whole recipe without partial reservations or energy use");
    var legacyItems = new zone.moddev.mc.lib.api.capabilities.ItemStorage(2);
    legacyItems.setStackInSlot(0, new ItemStack(Items.IRON_ORE));
    legacyItems.setStackInSlot(1, new ItemStack(Items.RAW_IRON, 63));
    var legacy = machine.saveWithFullMetadata(level.registryAccess());
    legacy.put("items", legacyItems.serializeNBT(level.registryAccess()));
    var upgraded =
        (ProcessorEntity)
            BlockEntity.loadStatic(pos, machine.getBlockState(), legacy, level.registryAccess());
    require(
        upgraded.items.getSlots() == 3
            && upgraded.items.getStackInSlot(1).getCount() == 63
            && upgraded.items.getStackInSlot(2).isEmpty(),
        "Existing two-slot crusher saves gain an empty second output without losing items");
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void oneSlotInterruptAndResume(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.TRANSMUTER);
    var level = helper.getLevel();
    var pos = machine.getBlockPos();
    var automation = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
    require(
        automation.insertItem(0, new ItemStack(Items.IRON_INGOT, 64), false).getCount() == 63,
        "The converter accepts exactly one iron");
    machine.energy.setStored(100_000);
    tick(machine, 51);
    require(machine.lanes.getFirst().progress() == 50, "Halfway to interrupt");
    require(automation.extractItem(0, 1, false).isEmpty(), "Running input is reserved");
    require(
        automation.insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1,
        "A hopper cannot refill the reserved slot");
    machine.energy.setStored(0);
    machine.serverTick();
    require(machine.lanes.getFirst().status() == Status.STALLED, "Stalls without power");
    require(
        machine.lanes.getFirst().completedCycles().signum() == 0,
        "Progress and energy stalls are not completed cycles");

    var player = helper.makeMockServerPlayerInLevel();
    player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    var menu = new MachineMenu(42, player.getInventory(), machine);
    player.containerMenu = menu;
    int button = menu.index(menu.bound.screen().find("toggle_0").orElseThrow());
    int action = new ScreenNetwork.Action(42, button, 0, 0, 0, 0).pack();
    menu.clickMenuButton(player, action);
    require(
        machine.getPaused() && !machine.items.hasPlan(0),
        "The screen frees reservations without power");
    require(automation.extractItem(0, 1, true).getCount() == 1, "Paused iron can be taken");
    require(
        machine.lanes.getFirst().completedCycles().signum() == 0,
        "Interrupts do not count as completed cycles");
    menu.clickMenuButton(player, action);
    machine.energy.setStored(100_000);
    tick(machine, 150);
    require(
        machine.items.getStackInSlot(0).is(Items.GOLD_INGOT),
        "Resume finishes from saved progress");
    require(
        machine.lanes.getFirst().completedCycles().equals(java.math.BigInteger.ONE),
        "A resumed recipe counts exactly once when it finishes");
    require(automation.extractItem(0, 1, false).is(Items.GOLD_INGOT), "Gold can be extracted");
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void sharedLanesSurviveSaveAndInterrupt(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.DUAL_CRUSHER);
    var level = helper.getLevel();
    var pos = machine.getBlockPos();
    machine.items.insertItem(0, new ItemStack(Items.COPPER_ORE, 2), false);
    machine.energy.setStored(100_000);
    tick(machine, 31);
    require(machine.items.plan(0).take() == 2, "Both lanes reserve the shared input");
    require(machine.items.plan(1).add().getCount() == 4, "Both lanes reserve the shared output");
    machine.toggle(0);
    require(machine.items.plan(0).take() == 1, "Interrupt releases only lane A's share");
    var saved = machine.saveWithFullMetadata(level.registryAccess());
    var copy =
        (ProcessorEntity)
            BlockEntity.loadStatic(pos, machine.getBlockState(), saved, level.registryAccess());
    copy.setLevel(level);
    require(
        copy.getPaused() && copy.lanes.get(1).status() == Status.RUNNING,
        "Independent lane states restored");
    require(copy.items.plan(0).take() == 1, "Lane B reservation restored");
    copy.toggle(0);
    tick(copy, 70);
    require(copy.items.getStackInSlot(0).isEmpty(), "Both lanes consumed their own ore");
    require(copy.items.getStackInSlot(1).getCount() == 4, "Both lanes produced exactly two copper");
    require(
        copy.screenStats().containsAll(copy.lanes)
            && copy.lanes.stream()
                .allMatch(lane -> lane.completedCycles().equals(java.math.BigInteger.ONE)),
        "Each processor exposes its own completed-cycle count");
    machine.clearContent();
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void commandReplacementClearsButDestroyDrops(GameTestHelper helper) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(CENTER);
    for (String command : new String[] {"setblock", "fill"}) {
      for (String mode : new String[] {"replace", "destroy"}) {
        for (var drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)))
          drop.discard();
        var machine = place(helper, CompanionMod.TRANSMUTER);
        machine.items.insertItem(0, new ItemStack(Items.IRON_INGOT), false);
        machine.energy.setStored(100_000);
        tick(machine, 5);
        String coordinates = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        String arguments = command.equals("fill") ? coordinates + " " + coordinates : coordinates;
        level
            .getServer()
            .getCommands()
            .performPrefixedCommand(
                level.getServer().createCommandSourceStack().withLevel(level).withPermission(4),
                command + " " + arguments + " minecraft:air " + mode);
        int iron =
            level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                .filter(drop -> drop.getItem().is(Items.IRON_INGOT))
                .mapToInt(drop -> drop.getItem().getCount())
                .sum();
        require(
            iron == (mode.equals("destroy") ? 1 : 0),
            command + " " + mode + " dropped " + iron + " iron");
      }
    }
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void crusherTankRejectsMixingAndPersists(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.CRUSHER);
    var level = helper.getLevel();
    var pos = machine.getBlockPos();
    for (var side : Direction.values()) {
      var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
      require(
          handler != null && handler.getTanks() == 1 && handler.getTankCapacity(0) == 4_000,
          "Crusher exposes one 4-bucket tank on every face");
      require(
          handler.fill(new FluidStack(Fluids.WATER, 5_000), FluidAction.SIMULATE) == 4_000,
          "Simulated fill respects capacity");
      require(machine.getTankFluid().isEmpty(), "Simulation leaves the tank empty");
    }
    var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.UP);
    require(
        handler.fill(new FluidStack(Fluids.WATER, 5_000), FluidAction.EXECUTE) == 4_000,
        "Water is accepted up to capacity");
    require(
        handler.fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE) == 0,
        "Full tank refuses overflow");
    handler.drain(3_999, FluidAction.EXECUTE);
    require(
        handler.fill(new FluidStack(Fluids.LAVA, 1_000), FluidAction.EXECUTE) == 0,
        "Even one mB of water prevents mixing with lava");
    require(
        handler.drain(new FluidStack(Fluids.LAVA, 1_000), FluidAction.EXECUTE).isEmpty(),
        "Draining the wrong fluid does not remove water");
    handler.drain(1, FluidAction.EXECUTE);
    require(
        handler.fill(new FluidStack(Fluids.LAVA, 2_000), FluidAction.EXECUTE) == 2_000,
        "An emptied tank accepts lava");
    require(
        handler.fill(new FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE) == 0,
        "Water cannot mix into lava either");
    var saved = machine.saveWithFullMetadata(level.registryAccess());
    var copy =
        (ProcessorEntity)
            BlockEntity.loadStatic(pos, machine.getBlockState(), saved, level.registryAccess());
    require(
        copy.getTankFluid().getFluid() == Fluids.LAVA && copy.getTankFluid().getAmount() == 2_000,
        "Fluid type and amount survive save/load");
    saved.remove("fluids");
    var legacy =
        (ProcessorEntity)
            BlockEntity.loadStatic(pos, machine.getBlockState(), saved, level.registryAccess());
    require(legacy.getTankFluid().isEmpty(), "Older saves start with an empty tank");
    machine.clearContent();
    require(machine.getTankFluid().isEmpty(), "Clearing the machine clears its fluid");
    for (var other : java.util.List.of(CompanionMod.TRANSMUTER, CompanionMod.DUAL_CRUSHER)) {
      place(helper, other);
      require(
          level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.UP) == null,
          "Only the single Crusher has a fluid tank");
    }
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void crusherBucketsFillDrainAndRefuseMixing(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.CRUSHER);
    var level = helper.getLevel();
    var pos = machine.getBlockPos();
    var player = helper.makeMockServerPlayerInLevel();
    player.getAbilities().instabuild = false;
    var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    var state = machine.getBlockState();
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
    require(
        state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit)
            == ItemInteractionResult.CONSUME,
        "Water bucket handled by the block");
    require(
        player.getMainHandItem().is(Items.BUCKET) && machine.getTankFluid().getAmount() == 1_000,
        "Filling consumes the water and returns an empty bucket");
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
    require(
        state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit)
            == ItemInteractionResult.FAIL,
        "Mixed bucket stops before vanilla can place lava");
    require(
        player.getMainHandItem().is(Items.LAVA_BUCKET)
            && machine.getTankFluid().getFluid() == Fluids.WATER
            && machine.getTankFluid().getAmount() == 1_000,
        "Rejected transfer leaves the bucket and tank unchanged");
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
    state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
    require(
        player.getMainHandItem().is(Items.WATER_BUCKET) && machine.getTankFluid().isEmpty(),
        "An empty bucket drains water");
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
    state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
    require(
        player.getMainHandItem().is(Items.BUCKET)
            && machine.getTankFluid().getFluid() == Fluids.LAVA,
        "Lava can be put in after draining water");
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
    state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
    require(
        player.getMainHandItem().is(Items.LAVA_BUCKET) && machine.getTankFluid().isEmpty(),
        "An empty bucket drains lava");
    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void tankGaugeTransfersCursorContainers(GameTestHelper helper) {
    var machine = place(helper, CompanionMod.CRUSHER);
    var player = helper.makeMockServerPlayerInLevel();
    var pos = machine.getBlockPos();
    player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    player.getAbilities().instabuild = false;
    // Mock players have no negotiated screen channel; the client smoke checks real synchronization.
    var menu =
        new MachineMenu(43, player.getInventory(), machine) {
          @Override
          public void broadcastChanges() {}
        };
    player.containerMenu = menu;
    int tank = menu.index(menu.bound.screen().find("tank").orElseThrow());
    int left = new ScreenNetwork.Action(43, tank, 0, 0, 0, 0).pack();
    int right = new ScreenNetwork.Action(43, tank, 0, 1, 0, 0).pack();

    menu.setCarried(new ItemStack(Items.WATER_BUCKET));
    menu.clickMenuButton(player, left);
    require(
        menu.getCarried().is(Items.BUCKET) && machine.getTankFluid().getAmount() == 1_000,
        "Clicking the gauge empties a cursor bucket and returns the empty container");
    menu.setCarried(new ItemStack(Items.LAVA_BUCKET));
    menu.clickMenuButton(player, left);
    require(
        menu.getCarried().is(Items.LAVA_BUCKET)
            && machine.getTankFluid().getFluid() == Fluids.WATER
            && machine.getTankFluid().getAmount() == 1_000,
        "Mixed cursor fluid leaves both sides untouched");
    menu.setCarried(new ItemStack(Items.BUCKET, 2));
    menu.clickMenuButton(player, right);
    require(
        menu.getCarried().is(Items.BUCKET) && menu.getCarried().getCount() == 1,
        "A stacked cursor processes exactly one container");
    require(
        player.getInventory().countItem(Items.WATER_BUCKET) == 1
            && machine.getTankFluid().isEmpty(),
        "The filled bucket goes to inventory without duplicating water");

    var container = new ItemStack(Items.GLASS_BOTTLE);
    FluidUtil.getFluidHandler(container)
        .orElseThrow()
        .fill(new FluidStack(Fluids.WATER, 1_500), FluidAction.EXECUTE);
    machine.fluids.fill(new FluidStack(Fluids.WATER, 3_500), FluidAction.EXECUTE);
    menu.setCarried(container);
    menu.clickMenuButton(player, left);
    require(
        machine.getTankFluid().getAmount() == 4_000
            && FluidUtil.getFluidHandler(menu.getCarried())
                    .orElseThrow()
                    .getFluidInTank(0)
                    .getAmount()
                == 1_000,
        "A modded container pours only the available room and retains its remainder");
    machine.fluids.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
    menu.clickMenuButton(player, left);
    require(
        machine.getTankFluid().getAmount() == 1_000
            && FluidUtil.getFluidHandler(menu.getCarried())
                .orElseThrow()
                .getFluidInTank(0)
                .isEmpty(),
        "The partial container can be completely emptied");
    menu.clickMenuButton(player, right);
    require(
        machine.getTankFluid().isEmpty()
            && FluidUtil.getFluidHandler(menu.getCarried())
                    .orElseThrow()
                    .getFluidInTank(0)
                    .getAmount()
                == 1_000,
        "An empty modded container can drain less than its capacity");
    menu.setCarried(new ItemStack(Items.IRON_INGOT));
    menu.clickMenuButton(player, left);
    require(
        menu.getCarried().is(Items.IRON_INGOT) && machine.getTankFluid().isEmpty(),
        "Non-container items are unchanged");
    menu.setCarried(new ItemStack(Items.WATER_BUCKET));
    player.setPos(pos.getX() + 100, pos.getY(), pos.getZ());
    menu.clickMenuButton(player, left);
    require(
        machine.getTankFluid().isEmpty() && menu.getCarried().is(Items.WATER_BUCKET),
        "A remote stale menu cannot transfer fluid");
    helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    helper.succeed();
  }

  private static ProcessorEntity place(GameTestHelper helper, CompanionMod.Machine machine) {
    var pos = helper.absolutePos(CENTER);
    helper.getLevel().setBlock(pos, machine.block.get().defaultBlockState(), 3);
    return (ProcessorEntity) helper.getLevel().getBlockEntity(pos);
  }

  private static void tick(ProcessorEntity machine, int ticks) {
    for (int i = 0; i < ticks; i++) machine.serverTick();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
